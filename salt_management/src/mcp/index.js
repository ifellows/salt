/**
 * MCP report-builder entry point. Opt-in via the MCP_ENABLED env flag so the
 * feature has zero effect on existing behaviour unless explicitly turned on.
 *
 * Usage from app.js (before the catch-all 404 handler):
 *   require('./mcp').init(app).finally(startServerAndFallbacks)
 */

const { mountMcp } = require('./server');

async function init(app) {
    if (String(process.env.MCP_ENABLED).toLowerCase() !== 'true') {
        console.log('[mcp] disabled (set MCP_ENABLED=true to enable the report builder MCP server)');
        return false;
    }
    await mountMcp(app);
    return true;
}

module.exports = { init };
