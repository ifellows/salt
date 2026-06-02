/**
 * reportInstructions.js — single source of truth for the report-generation
 * system prompt ("instructions").
 *
 * The text lives in an **editable Markdown file** so operators can tune the
 * guidance without a code change:
 *   - A shipped default ships with the code (report-instructions.default.md).
 *   - On first use it is seeded into the persistent data volume at
 *     data/reports/report-instructions.md (override with MCP_INSTRUCTIONS_FILE).
 *   - Edits to that file take effect on the next read (mtime-cached).
 *
 * Consumed by the MCP server (advertised `instructions`, the
 * `get_report_instructions` tool) and the `/instructions` endpoint, so the
 * guidance an agent receives never drifts between channels. The `{{SURVEY}}`
 * token is replaced per request with the survey context.
 */

const fs = require('fs');
const path = require('path');

const DEFAULT_FILE = path.join(__dirname, '..', 'mcp', 'report-instructions.default.md');

/** Path to the editable instructions file (env-overridable). */
function instructionsFilePath() {
    return process.env.MCP_INSTRUCTIONS_FILE
        || path.join(process.cwd(), 'data', 'reports', 'report-instructions.md');
}

let cache = { path: null, mtimeMs: -1, text: '' };

/** Ensure the editable file exists (seed from the shipped default once). */
function ensureSeeded() {
    const file = instructionsFilePath();
    if (!fs.existsSync(file)) {
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.copyFileSync(DEFAULT_FILE, file);
    }
    return file;
}

/** Load the template text, re-reading only when the file changes. */
function loadTemplate() {
    let file;
    try {
        file = ensureSeeded();
        const { mtimeMs } = fs.statSync(file);
        if (cache.path === file && cache.mtimeMs === mtimeMs) return cache.text;
        const text = fs.readFileSync(file, 'utf8');
        cache = { path: file, mtimeMs, text };
        return text;
    } catch (e) {
        // Fall back to the shipped default if the editable file is unreadable.
        try { return fs.readFileSync(DEFAULT_FILE, 'utf8'); } catch { return ''; }
    }
}

/**
 * @param {{surveyName?: string, surveyId?: number}} [ctx]
 * @returns {string} markdown instructions with {{SURVEY}} resolved
 */
function getReportInstructions(ctx = {}) {
    const target = ctx.surveyName
        ? `the survey **"${ctx.surveyName}"**${ctx.surveyId ? ` (id ${ctx.surveyId})` : ''}`
        : 'a SALT survey';
    return loadTemplate().replace(/\{\{SURVEY\}\}/g, target);
}

module.exports = { getReportInstructions, instructionsFilePath, ensureSeeded, DEFAULT_FILE };
