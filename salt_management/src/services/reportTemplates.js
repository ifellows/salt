/**
 * reportTemplates.js — single source of truth for the example Quarto report
 * templates, shared by the MCP tools (list_templates/get_template), the web
 * report editor's template picker, and the server-side seeding.
 *
 * Templates live in the data volume (editable), but that volume is
 * dockerignored/gitignored, so a shipped default set (src/mcp/templates-default)
 * is seeded into it on first use. Path is env-overridable via MCP_TEMPLATES_DIR.
 */

const fs = require('fs');
const path = require('path');

const DEFAULT_DIR = path.join(__dirname, '..', 'mcp', 'templates-default');

function templatesDir() {
    return process.env.MCP_TEMPLATES_DIR || path.join(process.cwd(), 'data', 'reports', 'templates');
}

/** Copy any missing shipped templates into the templates dir (never overwrites edits). */
function seedTemplates() {
    try {
        const dir = templatesDir();
        fs.mkdirSync(dir, { recursive: true });
        for (const f of fs.readdirSync(DEFAULT_DIR)) {
            if (!f.endsWith('.qmd')) continue;
            const target = path.join(dir, f);
            if (!fs.existsSync(target)) fs.copyFileSync(path.join(DEFAULT_DIR, f), target);
        }
    } catch (e) {
        console.warn('[templates] could not seed templates:', e.message);
    }
}

/** Prevent path traversal; templates are flat .qmd files. */
function safeName(name) {
    const base = path.basename(String(name || ''));
    return base.endsWith('.qmd') ? base : `${base}.qmd`;
}

/** Human label for a template: its YAML `title:` if present, else the filename. */
function titleOf(file, fileName) {
    try {
        const m = fs.readFileSync(file, 'utf8').match(/^title:\s*["']?(.+?)["']?\s*$/m);
        if (m && m[1].trim()) return m[1].trim();
    } catch { /* fall through */ }
    return fileName.replace(/\.qmd$/, '').replace(/[_-]+/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}

/** List available templates (seeds first). Returns [{ name, title }]. */
function listTemplates() {
    seedTemplates();
    const dir = templatesDir();
    let files = [];
    try { files = fs.readdirSync(dir).filter(f => f.endsWith('.qmd')); } catch { /* none */ }
    files.sort();
    return files.map(f => ({ name: f, title: titleOf(path.join(dir, f), f) }));
}

/** Read a template's contents (seeds first). Throws if not found. */
function getTemplate(name) {
    seedTemplates();
    return fs.readFileSync(path.join(templatesDir(), safeName(name)), 'utf8');
}

module.exports = { DEFAULT_DIR, templatesDir, seedTemplates, safeName, titleOf, listTemplates, getTemplate };
