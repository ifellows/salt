#!/usr/bin/env node
/**
 * backfill-survey-sections.js — ensure every survey has the required
 * 'eligibility' and 'main' sections, and that no question is left without a
 * section.
 *
 * Background: surveys created via the editor (and newly seeded surveys) get an
 * 'eligibility' section (index 0) and a 'main' section (index 1). An older seed
 * of the "SALT HIV Survey" created the survey with NO sections, leaving its
 * questions with section_id = NULL. This script repairs such surveys in an
 * existing database.
 *
 * For each survey it:
 *   - creates a 'eligibility' section if one is missing,
 *   - creates a 'main' section if one is missing,
 *   - assigns any question with section_id IS NULL to that survey's 'main' section.
 *
 * It is idempotent (safe to re-run) and does a dry run unless --yes is passed.
 * It never deletes sections; surveys carrying extra section types are only
 * reported (see "and only these two" note at the end).
 *
 * Usage:
 *   node scripts/backfill-survey-sections.js [--db data/database/salt.db] [--yes]
 *   SALT_DB_PATH=/path/to/salt.db node scripts/backfill-survey-sections.js --yes
 */

const path = require('path');
const sqlite3 = require('sqlite3');

const defaultDb = process.env.SALT_DB_PATH
    || path.join(__dirname, '..', 'data', 'database', 'salt.db');

const args = { db: defaultDb, yes: false };
for (let i = 2; i < process.argv.length; i++) {
    const k = process.argv[i];
    if (k === '--yes') args.yes = true;
    else if (k === '--db') args.db = process.argv[++i];
    else { console.error('Unknown arg:', k); process.exit(1); }
}

const db = new sqlite3.Database(args.db);
const run = (sql, p = []) => new Promise((res, rej) => db.run(sql, p, function (e) { e ? rej(e) : res(this); }));
const get = (sql, p = []) => new Promise((res, rej) => db.get(sql, p, (e, r) => e ? rej(e) : res(r)));
const all = (sql, p = []) => new Promise((res, rej) => db.all(sql, p, (e, r) => e ? rej(e) : res(r)));

async function ensureSection(survey, type, name, description, sections) {
    const existing = sections.find(s => s.section_type === type);
    if (existing) return existing.id;
    // Pick a non-colliding index: 0 for eligibility when free, else max+1.
    const maxIndex = sections.reduce((m, s) => Math.max(m, s.section_index), -1);
    const index = (type === 'eligibility' && !sections.some(s => s.section_index === 0)) ? 0 : maxIndex + 1;
    console.log(`  survey ${survey.id} "${survey.name}": missing '${type}' section -> create (index ${index})`);
    if (!args.yes) return null;
    const r = await run(
        `INSERT INTO sections (survey_id, section_index, section_type, name, description)
         VALUES (?, ?, ?, ?, ?)`,
        [survey.id, index, type, name, description]
    );
    sections.push({ id: r.lastID, section_index: index, section_type: type });
    return r.lastID;
}

(async () => {
    console.log(`Backfilling survey sections on ${args.db} (${args.yes ? 'APPLY' : 'dry run'})\n`);
    const surveys = await all('SELECT id, name FROM surveys ORDER BY id');
    let createdSections = 0, reassignedQuestions = 0;
    const extras = [];

    for (const survey of surveys) {
        const sections = await all(
            'SELECT id, section_index, section_type FROM sections WHERE survey_id = ? ORDER BY section_index',
            [survey.id]
        );
        const before = sections.length;
        await ensureSection(survey, 'eligibility', 'Eligibility', 'Screening questions to determine eligibility', sections);
        const mainId = await ensureSection(survey, 'main', 'Main', 'Primary survey questions', sections);
        createdSections += sections.length - before;

        // Assign orphaned questions to the main section.
        const orphan = (await get(
            'SELECT COUNT(*) n FROM questions WHERE survey_id = ? AND section_id IS NULL',
            [survey.id]
        )).n;
        if (orphan > 0) {
            console.log(`  survey ${survey.id} "${survey.name}": ${orphan} question(s) with no section -> assign to 'main'`);
            if (args.yes && mainId) {
                const r = await run(
                    'UPDATE questions SET section_id = ? WHERE survey_id = ? AND section_id IS NULL',
                    [mainId, survey.id]
                );
                reassignedQuestions += r.changes;
            } else if (!args.yes) {
                reassignedQuestions += orphan;
            }
        }

        // Report (do not touch) any section types beyond the required two.
        const extraTypes = sections
            .map(s => s.section_type)
            .filter(t => t !== 'eligibility' && t !== 'main');
        if (extraTypes.length) extras.push({ survey, extraTypes });
    }

    console.log(`\n${args.yes ? 'Created' : 'Would create'} ${createdSections} section(s); ` +
        `${args.yes ? 'reassigned' : 'would reassign'} ${reassignedQuestions} question(s).`);

    if (extras.length) {
        console.log('\nNote — surveys with section types other than eligibility/main (left untouched):');
        for (const e of extras) console.log(`  survey ${e.survey.id} "${e.survey.name}": ${[...new Set(e.extraTypes)].join(', ')}`);
    }
    if (!args.yes) console.log('\nRe-run with --yes to apply.');
    db.close();
})().catch(err => { console.error('Backfill failed:', err); db.close(); process.exit(1); });
