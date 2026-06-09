#!/usr/bin/env node
/**
 * seed-fake-data.js — populate a running SALT instance with fake completed-survey
 * data for its ACTIVE survey, as RDS recruitment chains spread across all
 * facilities, with answers that respect the survey's skip-logic.
 *
 * How it works (no server runtime code is touched):
 *   - reads facility API keys + the active survey id from salt.db,
 *   - downloads the canonical survey structure + each facility's config via the
 *     sync API (the same endpoints the tablet uses),
 *   - generates participants by walking the question flow exactly as the app
 *     does — evaluating pre_script (true = HIDE), validation_script, skip_to,
 *     and the survey eligibility_script via a JEXL adapter,
 *   - builds per-facility seed -> coupon -> recruit trees (RDS chains stay within
 *     a facility; seeds are spread across all facilities), and POSTs each
 *     submission to POST /api/sync/survey/upload with the owning facility's key.
 *   - includes a rapid-test result for each ENABLED test_configuration
 *     (HIV ~12% positive, others ~5%), sent in the upload payload.
 *   - LAB RESULTS are a separate ingestion path (not in the upload), so for each
 *     active lab test whose jexl_condition holds against the participant's rapid
 *     results (e.g. hiv == 'positive') it inserts a lab_results row DIRECTLY into
 *     the --db (attributed to an existing admin user for submitted_by).
 *
 * Everything is tagged so it is trivially removable (see cleanup-fake-data.js):
 *   survey_response_id = "fake-<uuid>", participant_id / lab subject_id =
 *   "FAKE-<facility>-<n>", device id = "fake-seeder".
 *
 * Usage:
 *   node scripts/seed-fake-data.js [--count 300] [--db data/database/salt.db]
 *       [--redemption 0.6] [--days 60] [--no-labs] [--dry-run]
 *   SALT_URL defaults to http://localhost:3000 (set it to hit another instance).
 *   Flags: --count N (participants), --redemption P (coupon->recruit prob),
 *          --days N (spread completions over last N days), --no-labs (skip lab
 *          results), --dry-run (generate + report only, no writes).
 *
 * SAFETY: defaults to localhost + supports --dry-run. Back up salt.db before a
 * live run:  cp data/database/salt.db salt.db.bak
 * NOTE: lab results are written straight to --db, so run this where that salt.db
 * is the SAME instance SALT_URL points at (i.e. on the server).
 */

const path = require('path');
const crypto = require('crypto');
const sqlite3 = require('sqlite3');
const jexl = require('jexl');

// ---- args ----------------------------------------------------------------
function parseArgs(argv) {
    const a = { count: 300, db: path.join('data', 'database', 'salt.db'), redemption: 0.6, days: 60, dryRun: false, labs: true };
    for (let i = 2; i < argv.length; i++) {
        const k = argv[i];
        if (k === '--dry-run') a.dryRun = true;
        else if (k === '--no-labs') a.labs = false;
        else if (k === '--count') a.count = parseInt(argv[++i], 10);
        else if (k === '--db') a.db = argv[++i];
        else if (k === '--redemption') a.redemption = parseFloat(argv[++i]);
        else if (k === '--days') a.days = parseInt(argv[++i], 10);
        else { console.error('Unknown arg:', k); process.exit(1); }
    }
    return a;
}
const ARGS = parseArgs(process.argv);
const SALT_URL = (process.env.SALT_URL || 'http://localhost:3000').replace(/\/$/, '');

// ---- tiny utils ----------------------------------------------------------
const uuid = () => crypto.randomUUID();
const randInt = (lo, hi) => lo + Math.floor(Math.random() * (hi - lo + 1));
const pick = (arr) => arr[randInt(0, arr.length - 1)];
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
function sampleK(arr, k) {
    const c = arr.slice();
    for (let i = c.length - 1; i > 0; i--) { const j = randInt(0, i);[c[i], c[j]] = [c[j], c[i]]; }
    return c.slice(0, k);
}
function englishText(j) {
    if (!j) return '';
    let o = j; if (typeof j === 'string') { try { o = JSON.parse(j); } catch { return j; } }
    if (o == null || typeof o !== 'object') return String(o);
    for (const key of ['en', 'En', 'EN', 'english', 'English', 'ENGLISH']) if (o[key]) return String(o[key]);
    const v = Object.values(o).find(x => x != null && x !== ''); return v != null ? String(v) : '';
}

// ---- sqlite (read-only) --------------------------------------------------
function openDb(file) {
    const db = new sqlite3.Database(file, sqlite3.OPEN_READONLY);
    const all = (sql, p = []) => new Promise((res, rej) => db.all(sql, p, (e, r) => e ? rej(e) : res(r)));
    return { db, all, close: () => db.close() };
}

// ---- JEXL adapter (match Apache Commons JEXL3 for the operators used) -----
jexl.addFunction('contains', (a, x) => Array.isArray(a) ? a.includes(x)
    : (a == null ? false : String(a).split(',').includes(String(x))));
jexl.addFunction('size', (a) => Array.isArray(a) ? a.length : (a == null ? 0 : String(a).length));
jexl.addFunction('matches', (s, re) => { try { return new RegExp(re).test(String(s)); } catch { return false; } });

function normalizeExpr(expr) {
    let e = String(expr);
    e = e.replace(/([\w.$\[\]]+)\.contains\s*\(/g, 'contains($1, ');
    e = e.replace(/([\w.$\[\]]+)\.size\s*\(\s*\)/g, 'size($1)');
    e = e.replace(/(\S+)\s*=~\s*('[^']*'|"[^"]*"|[\w.$]+)/g, 'matches($1, $2)');
    return e;
}
// Returns the raw evaluated value, or `dflt` on empty/error (app's safe default).
function evalScript(expr, context, dflt) {
    if (expr == null || String(expr).trim() === '') return dflt;
    try { return jexl.evalSync(normalizeExpr(expr), context); }
    catch { return dflt; }
}
const isHidden = (preScript, ctx) => evalScript(preScript, ctx, false) === true;       // true => HIDE
const isValid = (validScript, ctx) => { const r = evalScript(validScript, ctx, true); return !(r === null || r === false || r === undefined); };
const shouldJump = (skipScript, ctx) => evalScript(skipScript, ctx, false) === true;
function isEligible(script, ctx) {
    if (script == null || String(script).trim() === '') return true;
    const r = evalScript(script, ctx, true);
    if (typeof r === 'boolean') return r;
    if (typeof r === 'number') return r !== 0;
    if (typeof r === 'string') return r.toLowerCase() === 'true' || r === '1';
    if (r == null) return false;
    return String(r).toLowerCase() === 'true';
}

// ---- answer generation ----------------------------------------------------
const TEXT_SAMPLES = ['n/a', 'none', 'no comment', 'prefer not to say', 'other', 'see notes'];

function optionsFor(q, options) {
    return options.filter(o => o.question_id === q.id).sort((a, b) => a.option_index - b.option_index);
}

// Generate a type-valid answer for q, retrying until validation_script passes.
// Returns { answerType, answerValue (payload form), ctxValue, optionText } or last attempt.
function genAnswer(q, options, context) {
    const type = q.question_type;
    const opts = optionsFor(q, options);
    let last = null;
    for (let attempt = 0; attempt < 60; attempt++) {
        let cand;
        if (type === 'multiple_choice') {
            if (!opts.length) return null;
            const o = pick(opts);
            cand = { answerType: type, answerValue: o.option_index, ctxValue: o.option_index, optionText: englishText(o.option_text_json) };
        } else if (type === 'numeric') {
            const n = randInt(0, 120);
            cand = { answerType: type, answerValue: String(n), ctxValue: n, optionText: null };
        } else if (type === 'text') {
            const s = pick(TEXT_SAMPLES);
            cand = { answerType: type, answerValue: s, ctxValue: s, optionText: null };
        } else if (type === 'multi_select') {
            if (!opts.length) return null;
            const min = Math.max(1, q.min_selections || 1);
            const max = Math.min(opts.length, q.max_selections || opts.length);
            const k = randInt(min, Math.max(min, max));
            const idxs = sampleK(opts.map(o => o.option_index), k).sort((a, b) => a - b);
            cand = { answerType: type, answerValue: idxs.join(','), ctxValue: idxs, optionText: null };
        } else {
            return null; // info / unknown -> no answer
        }
        last = cand;
        if (isValid(q.validation_script, { ...context, value: cand.ctxValue })) return cand;
    }
    return last; // couldn't satisfy validation in 60 tries; server doesn't enforce it
}

// Walk the question flow as the app does (pre_script hide, skip_to jumps).
function generateOnce(survey, questions, options, idxByShort) {
    const context = {};
    const answers = [];
    const visited = new Set();
    let i = 0;
    while (i < questions.length) {
        if (visited.has(i)) { i++; continue; }
        visited.add(i);
        const q = questions[i];
        if (!q.short_name || q.question_type === 'info') { i++; continue; }
        if (isHidden(q.pre_script, context)) { context[q.short_name] = null; i++; continue; }
        const a = genAnswer(q, options, context);
        if (!a) { context[q.short_name] = null; i++; continue; }
        context[q.short_name] = a.ctxValue;
        answers.push({ questionShortName: q.short_name, questionId: q.id, answerType: a.answerType, answerValue: a.answerValue, optionText: a.optionText });
        let jumped = false;
        if (q.skip_to_script && q.skip_to_target && shouldJump(q.skip_to_script, { ...context, value: a.ctxValue })) {
            const t = idxByShort[q.skip_to_target];
            if (t != null && t > i) { i = t; jumped = true; }
        }
        if (!jumped) i++;
    }
    return { context, answers };
}

// Generate an ELIGIBLE participant (retry whole generation until eligible).
function generateEligible(survey, questions, options, idxByShort) {
    let last;
    for (let t = 0; t < 30; t++) {
        const p = generateOnce(survey, questions, options, idxByShort);
        if (isEligible(survey.eligibility_script, p.context)) return p;
        last = p;
    }
    return last; // give up after 30 tries; include anyway
}

// ---- rapid tests + payments ----------------------------------------------
function genTestResults(testConfigs, atIso) {
    return (testConfigs || []).filter(t => t.enabled).map(t => {
        const hiv = /hiv/i.test(t.test_id) || /hiv/i.test(t.test_name);
        const prev = hiv ? 0.12 : 0.05;
        return { testId: t.test_id, testName: t.test_name, result: Math.random() < prev ? 'positive' : 'negative', recordedAt: atIso };
    });
}
function genPayment(cfg, atIso, isSeed) {
    const type = cfg && cfg.subject_payment_type;
    if (!type || type === 'None') return {};
    const amount = isSeed ? (cfg.participation_payment_amount ?? 0) : (cfg.recruitment_payment_amount ?? cfg.participation_payment_amount ?? 0);
    return { paymentConfirmed: true, paymentAmount: amount, paymentType: type, paymentDate: atIso, sampleCollected: Math.random() < 0.85 };
}

// Lab results are a separate ingestion path (not in the survey upload). Each
// active lab test has a jexl_condition evaluated against the participant's RAPID
// test results (variable = test_id, value = result string, e.g. hiv=='positive').
// Returns rows for lab_results (inserted directly into the DB).
function genLabResults(labTests, rapidResults, subjectId, createdAtIso) {
    const ctx = {}; for (const t of rapidResults) ctx[t.testId] = t.result;
    const rows = [];
    for (const lt of (labTests || [])) {
        if (!lt.is_active) continue;
        const cond = lt.jexl_condition;
        const applies = (cond == null || String(cond).trim() === '') ? true : (evalScript(cond, ctx, false) === true);
        if (!applies) continue;
        if (lt.test_type === 'numeric') {
            const lo = lt.min_value != null ? lt.min_value : 0;
            const hi = lt.max_value != null ? lt.max_value : 100;
            rows.push({ subject_id: subjectId, test_id: lt.id, result_value: null, result_numeric: Math.round((lo + Math.random() * (hi - lo)) * 10) / 10, created_at: createdAtIso });
        } else {
            const opts = Array.isArray(lt.options) ? lt.options : (lt.options ? (() => { try { return JSON.parse(lt.options); } catch { return []; } })() : []);
            rows.push({ subject_id: subjectId, test_id: lt.id, result_value: String(opts.length ? pick(opts) : 'positive'), result_numeric: null, created_at: createdAtIso });
        }
    }
    return rows;
}

// ---- HTTP helpers ---------------------------------------------------------
async function apiGet(pathname, apiKey) {
    const r = await fetch(`${SALT_URL}${pathname}`, { headers: { Authorization: `Bearer ${apiKey}` } });
    if (!r.ok) throw new Error(`GET ${pathname} -> ${r.status}`);
    return r.json();
}
async function uploadSubmission(payload, apiKey) {
    const r = await fetch(`${SALT_URL}/api/sync/survey/upload`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
        body: JSON.stringify(payload),
    });
    const body = await r.json().catch(() => ({}));
    return { ok: r.ok, status: r.status, duplicate: !!(body.data && body.data.duplicate), body };
}

// ---- main -----------------------------------------------------------------
(async () => {
    const dbh = openDb(ARGS.db);
    const facilities = await dbh.all("SELECT id, name, api_key FROM facilities WHERE api_key IS NOT NULL AND api_key != ''");
    const activeSurvey = (await dbh.all('SELECT id, name FROM surveys WHERE is_active = 1 LIMIT 1'))[0];
    // For lab results (submitted_by is a NOT NULL FK to admin_users).
    const adminUser = (await dbh.all("SELECT id FROM admin_users WHERE is_active = 1 ORDER BY (role = 'administrator') DESC, id LIMIT 1"))[0];
    dbh.close();
    const submittedBy = adminUser ? adminUser.id : null;

    if (!facilities.length) { console.error('No facilities with an api_key found in', ARGS.db, '\nCreate a facility (admin -> Facilities) first.'); process.exit(1); }
    if (!activeSurvey) { console.error('No active survey (surveys.is_active=1) found.'); process.exit(1); }

    console.log(`Target: ${SALT_URL}  | active survey #${activeSurvey.id} "${activeSurvey.name}"`);
    console.log(`Facilities with keys: ${facilities.length}  | count=${ARGS.count} redemption=${ARGS.redemption} days=${ARGS.days}${ARGS.dryRun ? '  [DRY RUN]' : ''}`);

    // Survey structure (download once with the first facility key).
    const dl = (await apiGet('/api/sync/survey/download', facilities[0].api_key)).data;
    const survey = dl.survey;
    const questions = dl.questions;
    const options = dl.options;
    const testConfigs = dl.test_configurations || [];
    const labTests = (dl.lab_tests || []).filter(lt => lt.is_active);
    const wantLabs = ARGS.labs && labTests.length > 0;
    if (ARGS.labs && labTests.length && !submittedBy) console.warn('[labs] no active admin user for submitted_by — lab results will be skipped.');
    const language = (Array.isArray(survey.languages) && survey.languages[0]) || 'English';
    const idxByShort = {};
    questions.forEach((q, i) => { if (q.short_name) idxByShort[q.short_name] = i; });

    // Per-facility config (coupons + payments).
    for (const f of facilities) {
        try { f.config = (await apiGet('/api/sync/facility/config', f.api_key)).data || {}; }
        catch { f.config = {}; }
    }

    // ---- build a recruitment forest PER FACILITY (spread across all facilities) ----
    // Each facility gets ~count/numFacilities participants, grown as one or more
    // chains (seed -> coupons -> recruits) that stay within that facility.
    const now = Date.now(); const DAY = 86400000;
    const completedAtMs = {}; // surveyId -> ms (to keep recruits after their recruiter)
    const participants = [];
    const perFacility = Math.max(1, Math.ceil(ARGS.count / facilities.length));
    let remaining = ARGS.count;
    let globalN = 0;

    for (const facility of facilities) {
        if (remaining <= 0) break;
        const budget = Math.min(perFacility, remaining);
        const queue = [{ referralCoupon: null, recruiterSurveyId: null, depth: 0 }]; // start with a seed
        let made = 0;
        while (made < budget) {
            if (!queue.length) queue.push({ referralCoupon: null, recruiterSurveyId: null, depth: 0 }); // new seed if a tree died out
            const task = queue.shift();
            const surveyId = `fake-${uuid()}`;
            const subjectId = `FAKE-${facility.id}-${++globalN}`;

            // Interview timing. A recruit is NEVER interviewed before their recruiter:
            // the recruit COMPLETES strictly after the recruiter completed, and never in
            // the future. We place the recruit's completion in (recruiterCompleted, now],
            // biased to within ~30 days of the recruiter. (Capping at "now" guarantees no
            // future dates even for deep chains; seeds are random within the window.)
            const durMs = randInt(10, 30) * 60000; // interview duration
            const parentMs = task.recruiterSurveyId ? completedAtMs[task.recruiterSurveyId] : null;
            let cMs;
            if (parentMs != null) {
                const cap = Math.min(now, parentMs + 30 * DAY);   // within ~30 days, never future
                const floor = parentMs + durMs;                   // start (= completion - duration) >= recruiter completion
                cMs = (cap > floor)
                    // normal: strict start-ordering, biased SOON after the recruiter (square skews toward floor)
                    ? floor + Math.floor(Math.pow(Math.random(), 2) * (cap - floor))
                    : parentMs + 1 + Math.floor(Math.random() * Math.max(1, cap - parentMs)); // tight (recruiter ~ now): completed-ordering
            } else {
                cMs = now - randInt(0, ARGS.days) * DAY - randInt(0, 86399) * 1000; // seed: random in the window
            }
            completedAtMs[surveyId] = cMs;
            const completedAt = new Date(cMs).toISOString();
            const startedAt = new Date(cMs - durMs).toISOString();

            const { answers } = generateEligible(survey, questions, options, idxByShort);

            // issue coupons; redeemed ones become recruits in THIS facility
            const nIssue = Number(facility.config.coupons_to_issue ?? 3) || 0;
            const issued = [];
            for (let c = 0; c < nIssue; c++) issued.push(`FC-${uuid().slice(0, 8).toUpperCase()}`);
            for (const cc of issued) if (Math.random() < ARGS.redemption) queue.push({ referralCoupon: cc, recruiterSurveyId: surveyId, depth: task.depth + 1 });

            const testResults = genTestResults(testConfigs, completedAt);
            const labRows = (wantLabs && submittedBy) ? genLabResults(labTests, testResults, subjectId, completedAt) : [];
            const payload = {
                surveyId,
                serverSurveyId: activeSurvey.id,
                subjectId,
                startedAt, completedAt,
                language,
                deviceInfo: { deviceId: 'fake-seeder', deviceModel: 'FakeData Script', androidVersion: '14', appVersion: 'seed-1.0' },
                referralCouponCode: task.referralCoupon,
                issuedCoupons: issued,
                recruiter_survey_id: task.recruiterSurveyId || undefined,
                recruitment_depth: task.depth,
                answers,
                testResults,
                ...genPayment(facility.config, completedAt, task.depth === 0),
            };
            participants.push({ facility, depth: task.depth, payload, labRows, uploaded: false });
            made++; remaining--;
        }
    }

    // ---- report tree shape ----
    const byFacility = {}; const byDepth = {};
    for (const p of participants) { byFacility[p.facility.name] = (byFacility[p.facility.name] || 0) + 1; byDepth[p.depth] = (byDepth[p.depth] || 0) + 1; }
    console.log('\nGenerated', participants.length, 'participants');
    console.log(' per facility:', JSON.stringify(byFacility));
    console.log(' per depth   :', JSON.stringify(byDepth));
    const seeds = participants.filter(p => p.depth === 0).length;
    console.log(` seeds: ${seeds}  recruits: ${participants.length - seeds}`);
    const totalLabs = participants.reduce((n, p) => n + p.labRows.length, 0);
    console.log(` lab results: ${totalLabs}${wantLabs ? '' : ' (labs disabled)'}` + (wantLabs && !submittedBy ? ' (no admin user → skipped)' : ''));

    if (ARGS.dryRun) {
        const ex = participants.find(p => p.labRows.length) || participants.find(p => p.depth > 0) || participants[0];
        console.log('\n--- sample payload (depth ' + ex.depth + ', labRows ' + ex.labRows.length + ') ---');
        console.log(JSON.stringify({ ...ex.payload, _labRows: ex.labRows }, null, 2).slice(0, 2600));
        console.log('\n[DRY RUN] nothing uploaded.');
        return;
    }

    // ---- upload surveys (parents first; modest throttle) ----
    let created = 0, dup = 0, err = 0;
    for (const p of participants) {
        try {
            const r = await uploadSubmission(p.payload, p.facility.api_key);
            if (!r.ok) { err++; if (err <= 5) console.error('  upload failed', r.status, JSON.stringify(r.body).slice(0, 160)); }
            else { p.uploaded = true; if (r.duplicate) dup++; else created++; }
        } catch (e) { err++; if (err <= 5) console.error('  upload error', e.message); }
        if ((created + dup + err) % 50 === 0) { console.log(`  ...${created + dup + err}/${participants.length}`); await sleep(50); }
    }
    console.log(`\nDone (surveys). created=${created} duplicate=${dup} errors=${err}`);

    // ---- insert lab results directly into the DB (separate ingestion path) ----
    if (wantLabs && submittedBy) {
        const rows = participants.filter(p => p.uploaded).flatMap(p => p.labRows);
        if (rows.length) {
            const wdb = new sqlite3.Database(ARGS.db);
            const wrun = (sql, prm) => new Promise((res, rej) => wdb.run(sql, prm, function (e) { e ? rej(e) : res(this.changes); }));
            try {
                await wrun('BEGIN');
                for (const r of rows) {
                    await wrun(
                        `INSERT INTO lab_results (subject_id, test_id, result_value, result_numeric, submitted_by, created_at)
                         VALUES (?, ?, ?, ?, ?, ?)`,
                        [r.subject_id, r.test_id, r.result_value, r.result_numeric, submittedBy, r.created_at]
                    );
                }
                await wrun('COMMIT');
                console.log(`Inserted ${rows.length} lab_results (subject_id FAKE-...).`);
            } catch (e) { await wrun('ROLLBACK').catch(() => {}); console.error('lab insert failed:', e.message); }
            finally { wdb.close(); }
        }
    }

    console.log('Cleanup later with:  node scripts/cleanup-fake-data.js   (add --hard to remove rows)');
})().catch(e => { console.error('FATAL', e); process.exit(1); });
