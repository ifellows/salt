#!/usr/bin/env node
/**
 * mcp-mint-token.js — issue a short-lived MCP access token for a user, for
 * local testing and for clients that accept a static bearer header (e.g.
 * Claude Code via `claude mcp add --header "Authorization: Bearer <token>"`).
 *
 * Production claude.ai connections use the OAuth flow instead; this is a
 * convenience for development / CLI use. Tokens still obey the 6-hour cap.
 *
 * Usage: node scripts/mcp-mint-token.js [username]   (default: first admin)
 */

const { getAsync } = require('../src/models/database');
const { createSession } = require('../src/mcp/sessionStore');

(async () => {
    const username = process.argv[2];
    const user = username
        ? await getAsync("SELECT id, username, role FROM admin_users WHERE username = ? AND is_active = 1", [username])
        : await getAsync("SELECT id, username, role FROM admin_users WHERE role = 'administrator' AND is_active = 1 ORDER BY id LIMIT 1");

    if (!user) {
        console.error(username ? `No active user "${username}".` : 'No active administrator found.');
        process.exit(1);
    }
    if (user.role !== 'administrator') {
        console.error(`User "${user.username}" is not an administrator (role=${user.role}); MCP tools require administrator.`);
        process.exit(1);
    }

    const session = await createSession({ userId: user.id, clientId: 'local-mint', scope: 'mcp' });
    console.log(`# MCP access token for ${user.username} (id ${user.id})`);
    console.log(`# expires in ${session.expiresIn}s; session hard-caps at ${session.absoluteExpiresAt}`);
    console.log(session.accessToken);
    process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
