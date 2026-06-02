#!/usr/bin/env node
/**
 * cleanup-fake-data.js — remove the fake data created by seed-fake-data.js.
 *
 * Fakes are identified by survey_response_id LIKE 'fake-%'.
 *
 * Default (soft): set completed_surveys.deleted_at so exports/reports exclude
 *   them (the data stays in the DB, recoverable).
 * --hard: permanently DELETE the rows (completed_surveys + their survey_responses,
 *   rapid_test_results, survey_payments + matching coupon_usage + uploads).
 *
 * Usage:
 *   node scripts/cleanup-fake-data.js [--db data/database/salt.db] [--hard] [--yes]
 */

const path = require('path');
const sqlite3 = require('sqlite3');

const args = { db: path.join('data', 'database', 'salt.db'), hard: false, yes: false };
for (let i = 2; i < process.argv.length; i++) {
    const k = process.argv[i];
    if (k === '--hard') args.hard = true;
    else if (k === '--yes') args.yes = true;
    else if (k === '--db') args.db = process.argv[++i];
    else { console.error('Unknown arg:', k); process.exit(1); }
}

const db = new sqlite3.Database(args.db);
const run = (sql, p = []) => new Promise((res, rej) => db.run(sql, p, function (e) { e ? rej(e) : res(this.changes); }));
const get = (sql, p = []) => new Promise((res, rej) => db.get(sql, p, (e, r) => e ? rej(e) : res(r)));
const all = (sql, p = []) => new Promise((res, rej) => db.all(sql, p, (e, r) => e ? rej(e) : res(r)));

(async () => {
    const LIKE = "survey_response_id LIKE 'fake-%'";
    const total = (await get(`SELECT COUNT(*) n FROM completed_surveys WHERE ${LIKE}`)).n;
    const live = (await get(`SELECT COUNT(*) n FROM completed_surveys WHERE ${LIKE} AND deleted_at IS NULL`)).n;
    console.log(`Fake completed_surveys: ${total} total, ${live} not yet soft-deleted. Mode: ${args.hard ? 'HARD DELETE' : 'soft-delete'} on ${args.db}`);

    if (!total) { console.log('Nothing to clean up.'); db.close(); return; }
    if (!args.yes) {
        console.log('\nRe-run with --yes to proceed.' + (args.hard ? ' (--hard will PERMANENTLY delete rows)' : ''));
        db.close(); return;
    }

    await run('PRAGMA foreign_keys = ON');

    if (!args.hard) {
        const changed = await run(`UPDATE completed_surveys SET deleted_at = datetime('now') WHERE ${LIKE} AND deleted_at IS NULL`);
        console.log(`Soft-deleted ${changed} completed_surveys (set deleted_at). Exports/reports now exclude them.`);
        db.close(); return;
    }

    // Hard delete: children first (don't rely on cascade being enabled), then parents + side tables.
    const ids = (await all(`SELECT id FROM completed_surveys WHERE ${LIKE}`)).map(r => r.id);
    let respDel = 0, rapidDel = 0, payDel = 0;
    const CHUNK = 400;
    for (let i = 0; i < ids.length; i += CHUNK) {
        const slice = ids.slice(i, i + CHUNK);
        const ph = slice.map(() => '?').join(',');
        respDel += await run(`DELETE FROM survey_responses WHERE completed_survey_id IN (${ph})`, slice);
        rapidDel += await run(`DELETE FROM rapid_test_results WHERE completed_survey_id IN (${ph})`, slice).catch(() => 0);
        payDel += await run(`DELETE FROM survey_payments WHERE completed_survey_id IN (${ph})`, slice).catch(() => 0);
    }
    const couponDel = await run("DELETE FROM coupon_usage WHERE issued_by_survey_id LIKE 'fake-%' OR used_by_survey_id LIKE 'fake-%'").catch(() => 0);
    const uploadDel = await run("DELETE FROM uploads WHERE survey_response_id LIKE 'fake-%'").catch(() => 0);
    const csDel = await run(`DELETE FROM completed_surveys WHERE ${LIKE}`);
    console.log(`Hard-deleted: completed_surveys=${csDel}, survey_responses=${respDel}, rapid_test_results=${rapidDel}, survey_payments=${payDel}, coupon_usage=${couponDel}, uploads=${uploadDel}`);
    db.close();
})().catch(e => { console.error('FATAL', e); db.close(); process.exit(1); });
