# MCP report builder

Self-contained Model Context Protocol (MCP) server that lets an administrator's own AI
assistant (claude.ai, Claude Code, Claude Desktop) build and render Quarto reports against
this SALT instance, using their AI subscription. No participant rows leave the server.

Everything lives under `src/mcp/` and is **opt-in** via `MCP_ENABLED=true`. When disabled the
module mounts nothing and the rest of the server is unaffected.

## Layout

| File | Responsibility |
| --- | --- |
| `index.js` | `init(app)` gate (checks `MCP_ENABLED`) |
| `server.js` | `mountMcp(app)`: Streamable HTTP MCP endpoint at `/mcp` (stateless), bearer auth |
| `tools.js` | The MCP tool surface (wraps existing services); profile cache + render rate-limit |
| `oauth.js` | OAuth 2.1 authorization server (Path B) via the SDK's `mcpAuthRouter` + a consent page |
| `sessionStore.js` | Token issue/verify/refresh/revoke in `mcp_sessions`; enforces the 6h absolute cap |

Shared services it builds on (reusable elsewhere, not MCP-specific):
`src/services/dataDictionary.js`, `src/services/dataProfiler.js`,
`src/services/reportInstructions.js`. Rendering reuses `src/services/reportExecutor.js`
(extended with an optional `{runId, markdown}` arg) and the existing `reports` /
`report_runs` / `report_outputs` tables.

The MCP SDK is ESM; this is a CommonJS codebase, so the SDK is loaded via dynamic `import()`.

## Environment

| Var | Default | Meaning |
| --- | --- | --- |
| `MCP_ENABLED` | `false` | Master switch |
| `MCP_PUBLIC_URL` | `http://localhost:$PORT` | Public HTTPS base URL (OAuth issuer + resource id) |
| `MCP_ACCESS_TTL_SECONDS` | `1800` | Access-token lifetime |
| `MCP_SESSION_MAX_SECONDS` | `21600` | Absolute session cap (6h); refresh refused past it |
| `MCP_INSTRUCTIONS_FILE` | `data/reports/report-instructions.md` | Editable report-generation prompt (see below) |

## Editing the report instructions

The system prompt the AI receives is an **editable Markdown file**. The shipped default lives at
`src/mcp/report-instructions.default.md`; on first run it is copied to
`data/reports/report-instructions.md` (in the persistent data volume) and read from there.
Edit that file to tune the guidance — changes take effect on the next read (no restart needed).
The `{{SURVEY}}` token is replaced per request with the survey context. Set `MCP_INSTRUCTIONS_FILE`
to use a different path. Delete the file to re-seed the default.

## Database

`scripts/add-mcp.sql` (idempotent) creates `oauth_clients`, `oauth_auth_codes`,
`mcp_sessions`. The same tables are in `scripts/init-database.js` for fresh DBs.

```bash
sqlite3 data/database/salt.db < scripts/add-mcp.sql
```

## Auth model

- **claude.ai / Claude Code / Claude Desktop**: OAuth 2.1 with dynamic client registration and
  PKCE. `/authorize` reuses the existing SALT session login through an MCP-owned consent page
  (`/mcp-consent`), then issues a one-time code. Tokens come from `sessionStore`.
- **Local dev / static-header clients**: mint a token directly:
  ```bash
  node scripts/mcp-mint-token.js [username]   # default: first administrator
  ```
  Use it as `Authorization: Bearer <token>` (e.g. `claude mcp add --header ...`).

Sessions: short access token (silently refreshed) with an absolute 6-hour cap; `revoked`
supports instant logout. Only the `administrator` role may call tools; mutations are audited.

## Tools

`list_surveys`, `get_data_dictionary`, `get_data_profile`, `get_variable_summary`,
`list_templates`, `get_template`, `get_report_instructions`, `list_reports`, `get_report`,
`save_report`, `update_report`, `render_report`, `get_render_result`.

No tool returns row-level data. `render_report` is async (returns a `runId`); poll
`get_render_result` until `success`/`error`. On success it returns a capped markdown preview of
the rendered report (the report itself, with HTML/PDF/DOCX, lands in Reports → History).

## Discovery endpoints (when enabled)

- `/.well-known/oauth-authorization-server`
- `/.well-known/oauth-protected-resource/mcp`
- `/authorize`, `/token`, `/register`, `/revoke`, `/mcp`

## Known dependency

Render sandboxing is intentionally **not** included here: rendering executes report code on the
server exactly as the existing Reports engine already does. Hardening report execution (for all
run paths, not just MCP) is tracked as a separate feature.
