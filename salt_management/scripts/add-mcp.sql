-- Migration: MCP report-builder support (OAuth 2.1 authorization server + sessions).
-- Idempotent: safe to run repeatedly. Apply with:
--   sqlite3 data/database/salt.db < scripts/add-mcp.sql
--
-- These tables back the self-contained OAuth server (Path B) for the MCP
-- connector. They are isolated from the rest of the schema; no existing table
-- is modified. MCP renders reuse the existing reports/report_runs/report_outputs
-- pipeline (markdown is recorded as an ordinary 'md' report_output).

-- Dynamic client registrations (claude.ai and other MCP clients self-register).
CREATE TABLE IF NOT EXISTS oauth_clients (
    client_id TEXT PRIMARY KEY,
    client_secret_hash TEXT,                 -- NULL for public (PKCE) clients
    client_name TEXT,
    redirect_uris TEXT NOT NULL,             -- JSON array of allowed redirect URIs
    grant_types TEXT DEFAULT '["authorization_code","refresh_token"]',
    token_endpoint_auth_method TEXT DEFAULT 'none',
    metadata_json TEXT,                      -- full registration payload
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Transient authorization codes (PKCE). Short-lived; pruned on use/expiry.
CREATE TABLE IF NOT EXISTS oauth_auth_codes (
    code TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    redirect_uri TEXT NOT NULL,
    code_challenge TEXT,
    code_challenge_method TEXT DEFAULT 'S256',
    scope TEXT,
    expires_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Issued MCP sessions (access + refresh tokens, hashed). The 6-hour ABSOLUTE
-- cap lives in absolute_expires_at: refresh is refused once now > that value,
-- regardless of access-token lifetime. revoked supports instant logout.
CREATE TABLE IF NOT EXISTS mcp_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    client_id TEXT,
    access_token_hash TEXT,
    refresh_token_hash TEXT,
    scope TEXT,
    issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    access_expires_at DATETIME NOT NULL,     -- short-lived access token expiry
    absolute_expires_at DATETIME NOT NULL,   -- = issued_at + 6h (hard cap)
    last_used_at DATETIME,
    revoked INTEGER DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES admin_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mcp_sessions_access ON mcp_sessions(access_token_hash);
CREATE INDEX IF NOT EXISTS idx_mcp_sessions_refresh ON mcp_sessions(refresh_token_hash);
CREATE INDEX IF NOT EXISTS idx_mcp_sessions_user ON mcp_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_oauth_auth_codes_expiry ON oauth_auth_codes(expires_at);
