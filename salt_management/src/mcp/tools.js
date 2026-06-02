/**
 * tools.js — registers the MCP tool surface on an McpServer instance.
 *
 * Every tool is a thin wrapper over existing SALT services. No tool returns
 * row-level data: the agent sees schema (dictionary), aggregates (profile),
 * templates, report .qmd code, render status, and the executed-markdown
 * preview — never participant rows. Row data is read only by the server-side
 * render. Authorization is the administrator role (resolved from the bearer
 * token); mutations are audited.
 */

const fs = require('fs');
const path = require('path');
const { z } = require('zod');
const uuid = require('uuid');

const { allAsync, getAsync, runAsync } = require('../models/database');
const { generateDictionaryCsv, buildDictionaryRows } = require('../services/dataDictionary');
const DataProfiler = require('../services/dataProfiler');
const ReportExecutor = require('../services/reportExecutor');
const { getReportInstructions } = require('../services/reportInstructions');
const { logAudit } = require('../services/auditService');

// Example report templates. They live in the data volume (editable), but the
// volume is dockerignored/gitignored, so a shipped default set is seeded into it
// on first use. Path is env-overridable.
const DEFAULT_TEMPLATES_DIR = path.join(__dirname, 'templates-default');
const TEMPLATES_DIR = process.env.MCP_TEMPLATES_DIR || path.join(process.cwd(), 'data', 'reports', 'templates');

/** Copy any missing shipped templates into TEMPLATES_DIR (never overwrites edits). */
function seedTemplates() {
    try {
        fs.mkdirSync(TEMPLATES_DIR, { recursive: true });
        for (const f of fs.readdirSync(DEFAULT_TEMPLATES_DIR)) {
            if (!f.endsWith('.qmd')) continue;
            const target = path.join(TEMPLATES_DIR, f);
            if (!fs.existsSync(target)) fs.copyFileSync(path.join(DEFAULT_TEMPLATES_DIR, f), target);
        }
    } catch (e) {
        console.warn('[mcp] could not seed templates:', e.message);
    }
}
const PROFILE_CACHE_MS = parseInt(process.env.MCP_PROFILE_CACHE_MS || '300000', 10); // 5 min
// Truncation limits are intentionally very large — a backstop against a
// pathological payload, not a content limit. Override via env if needed.
const MAX_MD_CHARS = parseInt(process.env.MCP_MAX_MD_CHARS || '5000000', 10);
const MAX_LOG_CHARS = parseInt(process.env.MCP_MAX_LOG_CHARS || '1000000', 10);

const profiler = new DataProfiler();
const reportExecutor = new ReportExecutor();

// ---- small helpers --------------------------------------------------------

const text = (s) => ({ content: [{ type: 'text', text: String(s) }] });
const errorText = (s) => ({ content: [{ type: 'text', text: String(s) }], isError: true });

function userIdFrom(extra) {
    return extra && extra.authInfo && extra.authInfo.extra ? extra.authInfo.extra.userId : null;
}

function truncate(s, n) {
    if (s == null) return '';
    s = String(s);
    return s.length > n ? s.slice(0, n) + `\n\n[... truncated at ${n} characters ...]` : s;
}

/** Clean a render log for return: strip ANSI colour codes and collapse the
 *  bare / STDERR: / ERROR: triplication runQuarto produces into unique blocks. */
function sanitizeLog(s) {
    if (!s) return '';
    const noAnsi = String(s).replace(/\x1b\[[0-9;]*m/g, '');
    const seen = new Set();
    const kept = [];
    for (const part of noAnsi.split(/\n(?:STDERR|ERROR):\n/)) {
        const key = part.trim();
        if (key && !seen.has(key)) { seen.add(key); kept.push(key); }
    }
    return kept.join('\n\n');
}

// Short-TTL profile cache: profiling re-exports CSVs + runs R, so we avoid
// regenerating within a working session. Keyed by surveyId.
const profileCache = new Map();
async function getProfile(surveyId) {
    const hit = profileCache.get(surveyId);
    if (hit && Date.now() - hit.ts < PROFILE_CACHE_MS) return hit.text;
    const t = await profiler.generateProfile(surveyId);
    profileCache.set(surveyId, { text: t, ts: Date.now() });
    return t;
}

// Per-user render limiter (renders are expensive; cap spam).
const renderState = new Map(); // userId -> { inFlight, lastTs }
const MAX_INFLIGHT = 2;
const MIN_INTERVAL_MS = 3000;
function canRender(userId) {
    const s = renderState.get(userId) || { inFlight: 0, lastTs: 0 };
    if (s.inFlight >= MAX_INFLIGHT) return 'Too many renders in progress for your session — wait for one to finish.';
    if (Date.now() - s.lastTs < MIN_INTERVAL_MS) return 'Rendering too frequently — wait a moment and retry.';
    return null;
}
function markRenderStart(userId) {
    const s = renderState.get(userId) || { inFlight: 0, lastTs: 0 };
    s.inFlight += 1; s.lastTs = Date.now();
    renderState.set(userId, s);
}
function markRenderEnd(userId) {
    const s = renderState.get(userId);
    if (s) { s.inFlight = Math.max(0, s.inFlight - 1); renderState.set(userId, s); }
}

function safeTemplateName(name) {
    // prevent path traversal; templates are flat .qmd files
    const base = path.basename(String(name || ''));
    return base.endsWith('.qmd') ? base : `${base}.qmd`;
}

// ---- tool registration ----------------------------------------------------

function registerTools(server) {
    server.registerTool('list_surveys', {
        title: 'List surveys',
        description: 'List surveys with completed-response counts and date range.',
        inputSchema: {},
    }, async () => {
        const rows = await allAsync(`
            SELECT s.id, s.name, s.version, s.is_active,
                   (SELECT COUNT(*) FROM completed_surveys c WHERE c.survey_id = s.id AND c.deleted_at IS NULL) AS n_completed,
                   (SELECT MIN(completed_at) FROM completed_surveys c WHERE c.survey_id = s.id AND c.deleted_at IS NULL) AS first_completed,
                   (SELECT MAX(completed_at) FROM completed_surveys c WHERE c.survey_id = s.id AND c.deleted_at IS NULL) AS last_completed
            FROM surveys s ORDER BY s.id DESC`);
        return text(JSON.stringify(rows, null, 2));
    });

    server.registerTool('get_data_dictionary', {
        title: 'Get data dictionary',
        description: 'CSV data dictionary for a survey: one row per export variable (survey questions, rapid tests, labs, meta), with type, value labels, units, and the raw validation / skip / skip_to scripts.',
        inputSchema: { surveyId: z.number().int().describe('Survey id (see list_surveys)') },
    }, async ({ surveyId }) => {
        try {
            return text(await generateDictionaryCsv(surveyId));
        } catch (e) {
            return errorText(e.code === 'SURVEY_NOT_FOUND' ? `Survey ${surveyId} not found.` : `Failed: ${e.message}`);
        }
    });

    server.registerTool('get_data_profile', {
        title: 'Get data profile',
        description: 'Aggregate frequencies/summaries for every variable in a survey (table() for <=20 levels, summary() otherwise). Free-text values are suppressed. Aggregates only — no row data.',
        inputSchema: { surveyId: z.number().int() },
    }, async ({ surveyId }) => {
        try {
            return text(await getProfile(surveyId));
        } catch (e) {
            return errorText(`Failed to profile survey ${surveyId}: ${e.message}`);
        }
    });

    server.registerTool('get_variable_summary', {
        title: 'Get variable summary',
        description: 'Aggregate summary for a single variable (its block from the data profile).',
        inputSchema: {
            surveyId: z.number().int(),
            variable: z.string().describe('Exact variable name, e.g. q_age or rapid_hiv_result'),
        },
    }, async ({ surveyId, variable }) => {
        try {
            const profile = await getProfile(surveyId);
            const marker = `== ${variable} ==`;
            const idx = profile.indexOf(marker);
            if (idx !== -1) {
                const next = profile.indexOf('\n== ', idx + marker.length);
                return text(profile.slice(idx, next === -1 ? undefined : next).trim());
            }
            // Not in the profile — distinguish "known but no data" from "unknown variable"
            // so the message isn't misleading when the dataset is empty.
            let known = false;
            try { known = (await buildDictionaryRows(surveyId)).rows.some(r => r.variable === variable); } catch { /* fall through */ }
            const m = profile.match(/Total records:\s*(\d+)/);
            const total = m ? parseInt(m[1], 10) : null;
            if (known) {
                return text(`"${variable}" is defined in survey ${surveyId}'s data dictionary but has no summary` +
                    (total === 0 ? `: the dataset has 0 completed responses.` : `: no recorded values in the data.`));
            }
            return errorText(`Unknown variable "${variable}" for survey ${surveyId}. Use get_data_dictionary for valid names.`);
        } catch (e) {
            return errorText(`Failed: ${e.message}`);
        }
    });

    server.registerTool('list_templates', {
        title: 'List report templates',
        description: 'List the example Quarto (.qmd) report templates available as starting points.',
        inputSchema: {},
    }, async () => {
        seedTemplates();
        let files = [];
        try { files = fs.readdirSync(TEMPLATES_DIR).filter(f => f.endsWith('.qmd')); } catch { /* none */ }
        return text(JSON.stringify(files, null, 2));
    });

    server.registerTool('get_template', {
        title: 'Get report template',
        description: 'Return the contents of an example .qmd template.',
        inputSchema: { name: z.string().describe('Template file name, e.g. basic_summary.qmd') },
    }, async ({ name }) => {
        seedTemplates();
        const file = path.join(TEMPLATES_DIR, safeTemplateName(name));
        try { return text(fs.readFileSync(file, 'utf8')); }
        catch { return errorText(`Template not found: ${name}. Use list_templates.`); }
    });

    server.registerTool('get_report_instructions', {
        title: 'Get report instructions',
        description: 'CALL THIS FIRST, before any other tool or analysis. Required general guidance for building a SALT Quarto report: data contract, exact variable naming, data-cleaning rules, available R packages, and output requirements. (General — not survey-specific; use list_surveys/get_data_dictionary for a specific survey.)',
        inputSchema: {},
    }, async () => {
        return text(getReportInstructions());
    });

    server.registerTool('list_reports', {
        title: 'List reports',
        description: 'List saved reports.',
        inputSchema: {},
    }, async () => {
        const rows = await allAsync('SELECT id, name, description, is_active, created_at, updated_at FROM reports ORDER BY updated_at DESC LIMIT 100');
        return text(JSON.stringify(rows, null, 2));
    });

    server.registerTool('get_report', {
        title: 'Get report',
        description: 'Get a saved report including its .qmd content.',
        inputSchema: { reportId: z.number().int() },
    }, async ({ reportId }) => {
        const r = await getAsync('SELECT id, name, description, qmd_content, is_active FROM reports WHERE id = ?', [reportId]);
        if (!r) return errorText(`Report ${reportId} not found.`);
        return text(JSON.stringify(r, null, 2));
    });

    server.registerTool('save_report', {
        title: 'Save new report',
        description: 'Create a new report from .qmd content. Returns the new reportId.',
        inputSchema: {
            name: z.string().min(1),
            qmd: z.string().min(1).describe('Full Quarto .qmd document content'),
            description: z.string().optional(),
        },
    }, async ({ name, qmd, description }, extra) => {
        const userId = userIdFrom(extra);
        const result = await runAsync(
            `INSERT INTO reports (name, description, qmd_content, created_by, created_at, updated_at)
             VALUES (?, ?, ?, ?, datetime('now'), datetime('now'))`,
            [name, description || '', qmd, userId]
        );
        const reportId = result.id;
        logAudit(userId, 'create', 'report', reportId, null, { name, via: 'mcp' }).catch(() => {});
        return text(JSON.stringify({ reportId, message: 'Report saved. Use render_report to generate it.' }, null, 2));
    });

    server.registerTool('update_report', {
        title: 'Update report',
        description: 'Update an existing report. Provide only the fields to change. For small changes make minimal edits to qmd rather than regenerating the whole document.',
        inputSchema: {
            reportId: z.number().int(),
            qmd: z.string().optional(),
            name: z.string().optional(),
            description: z.string().optional(),
        },
    }, async ({ reportId, qmd, name, description }, extra) => {
        const existing = await getAsync('SELECT id FROM reports WHERE id = ?', [reportId]);
        if (!existing) return errorText(`Report ${reportId} not found.`);
        const sets = [], params = [];
        if (qmd !== undefined) { sets.push('qmd_content = ?'); params.push(qmd); }
        if (name !== undefined) { sets.push('name = ?'); params.push(name); }
        if (description !== undefined) { sets.push('description = ?'); params.push(description); }
        if (sets.length === 0) return errorText('Nothing to update — provide qmd, name, or description.');
        sets.push("updated_at = datetime('now')");
        params.push(reportId);
        await runAsync(`UPDATE reports SET ${sets.join(', ')} WHERE id = ?`, params);
        logAudit(userIdFrom(extra), 'update', 'report', reportId, null, { fields: sets, via: 'mcp' }).catch(() => {});
        return text(JSON.stringify({ reportId, message: 'Report updated.' }, null, 2));
    });

    server.registerTool('render_report', {
        title: 'Render report',
        description: 'Render a report to HTML/PDF/DOCX (and markdown for preview) in the background. Returns a runId; poll get_render_result until status is success or error.',
        inputSchema: { reportId: z.number().int() },
    }, async ({ reportId }, extra) => {
        const userId = userIdFrom(extra);
        const report = await getAsync('SELECT id FROM reports WHERE id = ? AND is_active = 1', [reportId]);
        if (!report) return errorText(`Report ${reportId} not found or inactive.`);

        const limit = canRender(userId);
        if (limit) return errorText(limit);

        const runId = uuid.v4();
        markRenderStart(userId);
        reportExecutor.executeReport(reportId, 'mcp', userId, { runId, markdown: true })
            .catch(err => console.error(`MCP render ${runId} failed:`, err.message))
            .finally(() => markRenderEnd(userId));
        logAudit(userId, 'render', 'report', reportId, null, { runId, via: 'mcp' }).catch(() => {});

        return text(JSON.stringify({ runId, status: 'running', message: 'Render started. Poll get_render_result with this runId.' }, null, 2));
    });

    server.registerTool('get_render_result', {
        title: 'Get render result',
        description: 'Get the status of a render. While running, status=running. On success, returns the executed markdown preview (capped). On error, returns the (truncated) R/Quarto log. Never returns raw participant rows.',
        inputSchema: { runId: z.string() },
    }, async ({ runId }) => {
        const run = await getAsync('SELECT * FROM report_runs WHERE run_id = ?', [runId]);
        if (!run) return text(JSON.stringify({ runId, status: 'running', message: 'No record yet — render is starting. Poll again shortly.' }, null, 2));

        if (run.status === 'running') {
            return text(JSON.stringify({ runId, status: 'running', message: 'Still rendering. Poll again shortly.' }, null, 2));
        }
        if (run.status === 'failed') {
            return text(JSON.stringify({
                runId, status: 'error',
                error: run.error_message || 'Render failed',
                log: truncate(sanitizeLog(run.log_output), MAX_LOG_CHARS),
                note: 'Log is truncated and best-effort; not guaranteed free of data values.',
            }, null, 2));
        }
        // completed
        const md = await getAsync("SELECT file_path FROM report_outputs WHERE run_id = ? AND file_type = 'md' LIMIT 1", [runId]);
        let markdown = '';
        if (md && md.file_path) {
            try { markdown = truncate(fs.readFileSync(md.file_path, 'utf8'), MAX_MD_CHARS); }
            catch { markdown = '(markdown output unavailable)'; }
        }
        return text(JSON.stringify({
            runId, status: 'success',
            message: 'Render complete. The report is available in Reports → History (HTML/PDF/DOCX).',
            markdown_preview: markdown,
        }, null, 2));
    });
}

module.exports = { registerTools, seedTemplates, _internals: { getProfile, profileCache } };
