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

usage() {
    sed -n '3,18p' "$0"
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --upstream) UPSTREAM_PORT="$2"; shift 2 ;;
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

# Pull the stock default site out of the way (it claims port 80 on '_').
if [[ -L "$DEFAULT_LINK" ]]; then
    echo "[setup-nginx] Disabling default nginx site"
    rm -f "$DEFAULT_LINK"
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
