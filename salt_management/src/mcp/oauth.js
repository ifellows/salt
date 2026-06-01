/**
 * oauth.js — self-contained OAuth 2.1 authorization server for the MCP connector
 * (Path B). Backed by the oauth_clients / oauth_auth_codes / mcp_sessions tables.
 *
 * Uses the MCP SDK's `mcpAuthRouter` for the protocol plumbing (discovery
 * metadata, dynamic client registration, PKCE validation, token + revoke
 * endpoints, built-in rate limiting). We supply the provider: dynamic client
 * store, the authorize() user-consent step (which reuses SALT's existing
 * session login via an MCP-owned consent page — no change to shared login),
 * and token issuance via the shared sessionStore (which enforces the 6h cap).
 */

const { runAsync, getAsync } = require('../models/database');
const sessionStore = require('./sessionStore');

const AUTH_CODE_TTL_MS = 10 * 60 * 1000; // 10 minutes

// ---- client store ---------------------------------------------------------

const clientsStore = {
    async getClient(clientId) {
        const row = await getAsync('SELECT metadata_json FROM oauth_clients WHERE client_id = ?', [clientId]);
        if (!row) return undefined;
        try { return JSON.parse(row.metadata_json); } catch { return undefined; }
    },

    async registerClient(client) {
        const clientId = sessionStore.randomToken().slice(0, 24);
        const full = {
            ...client,
            client_id: clientId,
            client_id_issued_at: Math.floor(Date.now() / 1000),
            token_endpoint_auth_method: client.token_endpoint_auth_method || 'none',
        };
        await runAsync(
            `INSERT INTO oauth_clients (client_id, client_name, redirect_uris, grant_types, token_endpoint_auth_method, metadata_json)
             VALUES (?, ?, ?, ?, ?, ?)`,
            [
                clientId,
                full.client_name || null,
                JSON.stringify(full.redirect_uris || []),
                JSON.stringify(full.grant_types || ['authorization_code', 'refresh_token']),
                full.token_endpoint_auth_method,
                JSON.stringify(full),
            ]
        );
        return full;
    },
};

// ---- provider -------------------------------------------------------------

function localPath(url) {
    // Only allow same-origin relative paths through the consent round-trip.
    return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//');
}

const provider = {
    get clientsStore() { return clientsStore; },

    /**
     * Authorization step. The SDK has already validated the client and PKCE
     * params; we ensure the browser has an authenticated SALT session and an
     * explicit consent, then redirect back to the client's redirect_uri with a
     * one-time code.
     */
    async authorize(client, params, res) {
        const req = res.req;
        const approved = req.query && req.query.mcp_approved === '1';

        if (!req.session || !req.session.userId || !approved) {
            // Bounce to the MCP consent page, preserving the full authorize URL.
            const back = encodeURIComponent(req.originalUrl);
            return res.redirect(`/mcp-consent?return=${back}`);
        }

        const code = sessionStore.randomToken();
        const expiresAt = new Date(Date.now() + AUTH_CODE_TTL_MS).toISOString();
        await runAsync(
            `INSERT INTO oauth_auth_codes (code, client_id, user_id, redirect_uri, code_challenge, code_challenge_method, scope, expires_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
            [
                code, client.client_id, req.session.userId, params.redirectUri,
                params.codeChallenge || null, 'S256',
                (params.scopes || ['mcp']).join(' '), expiresAt,
            ]
        );

        const url = new URL(params.redirectUri);
        url.searchParams.set('code', code);
        if (params.state) url.searchParams.set('state', params.state);
        res.redirect(url.toString());
    },

    async challengeForAuthorizationCode(client, authorizationCode) {
        const row = await getAsync(
            'SELECT code_challenge FROM oauth_auth_codes WHERE code = ? AND client_id = ?',
            [authorizationCode, client.client_id]
        );
        return row ? row.code_challenge : undefined;
    },

    async exchangeAuthorizationCode(client, authorizationCode, codeVerifier, redirectUri) {
        const row = await getAsync('SELECT * FROM oauth_auth_codes WHERE code = ?', [authorizationCode]);
        if (!row || row.client_id !== client.client_id) {
            throw new Error('invalid_grant: unknown authorization code');
        }
        // One-time use.
        await runAsync('DELETE FROM oauth_auth_codes WHERE code = ?', [authorizationCode]);
        if (Date.now() > new Date(row.expires_at).getTime()) {
            throw new Error('invalid_grant: authorization code expired');
        }
        if (redirectUri && redirectUri !== row.redirect_uri) {
            throw new Error('invalid_grant: redirect_uri mismatch');
        }

        const session = await sessionStore.createSession({
            userId: row.user_id, clientId: client.client_id, scope: row.scope || 'mcp',
        });
        return {
            access_token: session.accessToken,
            token_type: 'bearer',
            expires_in: session.expiresIn,
            refresh_token: session.refreshToken,
            scope: session.scope,
        };
    },

    async exchangeRefreshToken(client, refreshToken) {
        const refreshed = await sessionStore.refreshSession(refreshToken, client.client_id);
        if (!refreshed) {
            // Past the absolute 6h cap, revoked, or invalid → force re-authentication.
            throw new Error('invalid_grant: refresh token invalid or session expired');
        }
        return {
            access_token: refreshed.accessToken,
            token_type: 'bearer',
            expires_in: refreshed.expiresIn,
            refresh_token: refreshed.refreshToken,
            scope: refreshed.scope,
        };
    },

    async verifyAccessToken(token) {
        const info = await sessionStore.verifyAccessToken(token);
        if (!info) throw new Error('invalid_token');
        return info;
    },

    async revokeToken(client, request) {
        await sessionStore.revokeToken(request.token);
    },
};

// ---- consent page (self-contained; reuses POST /api/auth/login) -----------

function consentPage({ loggedIn, username, returnUrl }) {
    const safeReturn = localPath(returnUrl) ? returnUrl : '/';
    const allowUrl = safeReturn + (safeReturn.includes('?') ? '&' : '?') + 'mcp_approved=1';
    const body = loggedIn
        ? `<p>Signed in as <strong>${escapeHtml(username)}</strong>.</p>
           <p>Allow this application to build and render SALT reports on your behalf?
              It can read survey data dictionaries and aggregate summaries, and create/render
              reports — it never receives individual participant records.</p>
           <div class="row">
             <a class="btn allow" href="${escapeHtml(allowUrl)}">Allow</a>
             <a class="btn deny" href="/">Deny</a>
           </div>`
        : `<p>Sign in to your SALT administrator account to connect.</p>
           <form id="f">
             <input id="u" placeholder="Username" autocomplete="username" />
             <input id="p" type="password" placeholder="Password" autocomplete="current-password" />
             <button class="btn allow" type="submit">Sign in</button>
             <div id="err" class="err"></div>
           </form>
           <script>
             document.getElementById('f').addEventListener('submit', async (e) => {
               e.preventDefault();
               const r = await fetch('/api/auth/login', {
                 method:'POST', headers:{'Content-Type':'application/json'}, credentials:'same-origin',
                 body: JSON.stringify({ username: document.getElementById('u').value, password: document.getElementById('p').value })
               });
               if (r.ok) { location.reload(); }
               else { const d = await r.json().catch(()=>({})); document.getElementById('err').textContent = d.error || 'Login failed'; }
             });
           </script>`;

    return `<!doctype html><html><head><meta charset="utf-8"><title>Connect to SALT</title>
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <style>
        body{font-family:system-ui,Arial,sans-serif;background:#f5f6f8;margin:0;display:flex;min-height:100vh;align-items:center;justify-content:center}
        .card{background:#fff;max-width:420px;padding:2rem;border-radius:10px;box-shadow:0 2px 16px rgba(0,0,0,.08)}
        h1{font-size:1.2rem;margin:0 0 1rem} p{color:#333;line-height:1.45}
        input{display:block;width:100%;padding:.6rem;margin:.4rem 0;border:1px solid #ccc;border-radius:6px;box-sizing:border-box}
        .btn{display:inline-block;padding:.6rem 1.1rem;border-radius:6px;text-decoration:none;border:0;cursor:pointer;font-size:1rem;margin-top:.5rem}
        .allow{background:#2563eb;color:#fff} .deny{background:#e5e7eb;color:#111;margin-left:.5rem}
        .row{margin-top:1rem} .err{color:#b91c1c;margin-top:.5rem;font-size:.9rem}
      </style></head>
      <body><div class="card"><h1>Connect to SALT report builder</h1>${body}</div></body></html>`;
}

function escapeHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => (
        { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
}

// ---- mount ----------------------------------------------------------------

async function mountOAuth(app, { baseUrl }) {
    const { mcpAuthRouter, getOAuthProtectedResourceMetadataUrl } =
        await import('@modelcontextprotocol/sdk/server/auth/router.js');

    const issuerUrl = new URL(baseUrl);
    const resourceServerUrl = new URL(baseUrl.replace(/\/$/, '') + '/mcp');

    // Consent page (MCP-owned; reuses the existing login API for credentials).
    app.get('/mcp-consent', async (req, res) => {
        const returnUrl = req.query.return ? decodeURIComponent(String(req.query.return)) : '/';
        let username = '';
        const loggedIn = !!(req.session && req.session.userId);
        if (loggedIn) {
            const u = await getAsync('SELECT username FROM admin_users WHERE id = ?', [req.session.userId]);
            username = u ? u.username : '';
        }
        res.set('Content-Type', 'text/html').send(consentPage({ loggedIn, username, returnUrl }));
    });

    app.use(mcpAuthRouter({
        provider,
        issuerUrl,
        baseUrl: issuerUrl,
        resourceServerUrl,
        scopesSupported: ['mcp'],
        resourceName: 'SALT report builder',
    }));

    console.log(`[mcp] OAuth authorization server mounted (issuer ${issuerUrl.href})`);
    return getOAuthProtectedResourceMetadataUrl(resourceServerUrl);
}

module.exports = { mountOAuth, provider, clientsStore };
