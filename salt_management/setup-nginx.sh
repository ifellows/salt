#!/bin/bash
#
# Stand up an nginx reverse proxy in front of the SALT container with a
# Let's Encrypt TLS cert. One-shot installer for a fresh Debian/Ubuntu host.
#
# Usage:
#   sudo ./setup-nginx.sh <domain> <admin-email> [--upstream PORT]
#
# Example:
#   sudo ./setup-nginx.sh salt.example.org admin@example.org
#
# What it does:
#   1. Installs nginx + certbot + the nginx certbot plugin if missing
#   2. Writes /etc/nginx/sites-available/salt with a reverse proxy to
#      127.0.0.1:<port> (default 3000)
#   3. Enables the site, removes the default catch-all
#   4. Tests the nginx config, reloads
#   5. Issues a Let's Encrypt cert via certbot --nginx, which also rewrites
#      the server block to redirect HTTP→HTTPS
#   6. Verifies certbot's renewal timer is active
#
# Idempotent. Re-running upgrades certs and refreshes the site config.

set -euo pipefail

DOMAIN=""
EMAIL=""
UPSTREAM_PORT=3000
PROCEED=0

usage() {
    sed -n '3,18p' "$0"
    echo
    echo "  --proceed   Required to continue when an existing nginx with enabled"
    echo "              sites is detected (blast-radius guard)."
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --upstream) UPSTREAM_PORT="$2"; shift 2 ;;
        --proceed)  PROCEED=1; shift ;;
        -h|--help) usage ;;
        -*) echo "Unknown flag: $1" >&2; usage ;;
        *)
            if [[ -z "$DOMAIN" ]]; then DOMAIN="$1"
            elif [[ -z "$EMAIL" ]]; then EMAIL="$1"
            else echo "Unexpected argument: $1" >&2; usage
            fi
            shift
            ;;
    esac
done

if [[ -z "$DOMAIN" || -z "$EMAIL" ]]; then
    usage
fi

if [[ "$EUID" -ne 0 ]]; then
    echo "This script needs root (writes to /etc/nginx and runs apt). Try: sudo $0 ..." >&2
    exit 1
fi

if ! grep -qiE 'debian|ubuntu' /etc/os-release 2>/dev/null; then
    echo "Warning: this installer was written for Debian/Ubuntu. Continuing, but apt commands may fail on other distros." >&2
fi

# --- Blast-radius guard ----------------------------------------------------
# If nginx is ALREADY installed and has enabled sites, this is a non-trivial
# host. This script adds a 'salt' site, disables the 'default' site, and runs
# certbot — all of which touch shared nginx state. Make the operator opt in.
if command -v nginx >/dev/null 2>&1; then
    EXISTING_SITES=()
    if [[ -d /etc/nginx/sites-enabled ]]; then
        for site in /etc/nginx/sites-enabled/*; do
            [[ -e "$site" || -L "$site" ]] || continue
            base="$(basename "$site")"
            [[ "$base" == "salt" ]] && continue   # our own site from a prior run
            EXISTING_SITES+=("$base")
        done
    fi
    if [[ ${#EXISTING_SITES[@]} -gt 0 && "$PROCEED" -ne 1 ]]; then
        cat >&2 <<WARN

================================================================
WARNING: existing nginx detected with enabled site(s):
    ${EXISTING_SITES[*]}

This script will, on this host:
  - add an nginx site 'salt' (server_name $DOMAIN)
  - disable the 'default' site (its config is backed up to
    /etc/nginx/sites-available/default_old — not deleted)
  - run certbot, which edits nginx config in place and reloads

Other sites are left alone, but a server_name collision or a
pre-existing config error will surface here.

Re-run with --proceed once you've reviewed this. To skip nginx
entirely and wire your own proxy at 127.0.0.1:$UPSTREAM_PORT, run
install.sh with --skip-nginx.
================================================================
WARN
        exit 1
    fi
fi

echo "[setup-nginx] Installing nginx + certbot..."
apt-get update -qq
apt-get install -y -qq nginx certbot python3-certbot-nginx

SITE_CONF=/etc/nginx/sites-available/salt
ENABLED_LINK=/etc/nginx/sites-enabled/salt
DEFAULT_LINK=/etc/nginx/sites-enabled/default

echo "[setup-nginx] Writing site config to $SITE_CONF"
cat > "$SITE_CONF" <<NGINX
# SALT Management Server — reverse proxy site (HTTP-only at first; certbot
# will rewrite this server block to enable HTTPS + redirect after the cert
# is issued).
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    # Survey upload payloads include audio in base64; bump the body size cap
    # accordingly. Match the express.json limit in src/app.js.
    client_max_body_size 50M;

    # Logs
    access_log /var/log/nginx/salt.access.log;
    error_log  /var/log/nginx/salt.error.log;

    # ACME challenge served from disk; certbot manages this path
    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        proxy_pass http://127.0.0.1:$UPSTREAM_PORT;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";

        # Generous timeouts for large survey uploads / report renders
        proxy_read_timeout 300s;
        proxy_connect_timeout 30s;
        proxy_send_timeout 300s;
    }
}
NGINX

ln -sf "$SITE_CONF" "$ENABLED_LINK"

# Pull the default site out of the way (it claims port 80 on the '_'
# catch-all). Back it up rather than deleting it — an admin may have
# customized it.
if [[ -L "$DEFAULT_LINK" || -e "$DEFAULT_LINK" ]]; then
    DEFAULT_BACKUP=/etc/nginx/sites-available/default_old
    if [[ -L "$DEFAULT_LINK" ]]; then
        # Symlink: copy the real config it points at, then drop the link.
        DEFAULT_TARGET="$(readlink -f "$DEFAULT_LINK" 2>/dev/null || true)"
        if [[ -n "$DEFAULT_TARGET" && -f "$DEFAULT_TARGET" && ! -e "$DEFAULT_BACKUP" ]]; then
            cp -a "$DEFAULT_TARGET" "$DEFAULT_BACKUP"
        fi
        rm -f "$DEFAULT_LINK"
    else
        # Regular file directly in sites-enabled: move it out.
        [[ -e "$DEFAULT_BACKUP" ]] || mv "$DEFAULT_LINK" "$DEFAULT_BACKUP"
        rm -f "$DEFAULT_LINK"
    fi
    echo "[setup-nginx] Default site disabled (config preserved at $DEFAULT_BACKUP)"
fi

echo "[setup-nginx] Testing nginx config"
nginx -t

echo "[setup-nginx] Reloading nginx"
systemctl reload nginx || systemctl restart nginx

echo "[setup-nginx] Requesting/renewing Let's Encrypt cert for $DOMAIN"
certbot --nginx \
    --non-interactive \
    --agree-tos \
    --email "$EMAIL" \
    --domain "$DOMAIN" \
    --redirect

echo "[setup-nginx] Confirming renewal timer is active"
systemctl list-timers 'certbot*' --all | head -3 || true

cat <<DONE

================================================================
SALT reverse proxy is live: https://$DOMAIN
================================================================

Upstream  : 127.0.0.1:$UPSTREAM_PORT
Config    : $SITE_CONF
Logs      : /var/log/nginx/salt.{access,error}.log
Certs     : managed by certbot ('certbot certificates' to inspect)

If you haven't already, start the SALT container so the upstream is reachable:

    docker compose -f /path/to/salt_management/docker-compose.yml up -d

DONE
