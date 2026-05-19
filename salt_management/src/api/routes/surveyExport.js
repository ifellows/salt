/**
 * Survey Export / Import
 *
 *   GET  /api/admin/surveys/:id/export   → JSON file download
 *   POST /api/admin/surveys/import       → JSON body, creates a new survey
 *
 * Export includes everything needed to re-create the survey on another
 * deployment: the surveys row, sections, questions, options, survey_messages,
 * and test_configurations. Audio is embedded as base64 in the same
 * `audio_files_json` shape the database uses, so the file is self-contained.
 *
 * NOT included: lab_test_configurations (global to the deployment),
 * facilities, admin users, completed survey data, coupons, audit logs.
 *
 * Import always creates a brand new survey row (is_active=0, is_draft=1).
 * Internal IDs (survey/section/question/option) are reassigned on insert and
 * references within the bundle are remapped accordingly. There is no in-place
 * overwrite path — that would require diff/merge logic that isn't worth the
 * risk for v1.
 */

const express = require('express');
const { getAsync, allAsync, runAsync } = require('../../models/database');
const { requireAdmin } = require('../middleware/auth');
const { logAudit } = require('../../services/auditService');
const router = express.Router();

const SCHEMA_VERSION = 1;

const SURVEY_COLUMNS = [
    'name', 'description', 'languages', 'version', 'is_active', 'eligibility_script',
    'eligibility_message_json', 'base_survey_id', 'parent_survey_id', 'version_notes',
    'is_draft', 'fingerprint_enabled', 're_enrollment_days',
    'staff_validation_message_json', 'hiv_rapid_test_enabled', 'contact_info_enabled',
    'staff_eligibility_screening', 'rapid_test_samples_after_eligibility',
    'payment_audit_phone_enabled'
];

function safeFilename(s) {
    return String(s || 'survey').replace(/[^A-Za-z0-9._-]+/g, '_').slice(0, 60);
}

router.get('/admin/surveys/:id/export', requireAdmin, async (req, res) => {
    try {
        const id = parseInt(req.params.id, 10);
        if (!Number.isInteger(id)) {
            return res.status(400).json({ status: 'error', message: 'Invalid survey id' });
        }

        const survey = await getAsync('SELECT * FROM surveys WHERE id = ?', [id]);
        if (!survey) {
            return res.status(404).json({ status: 'error', message: 'Survey not found' });
        }
        const sections = await allAsync(
            'SELECT * FROM sections WHERE survey_id = ? ORDER BY section_index', [id]
        );
        const questions = await allAsync(
            'SELECT * FROM questions WHERE survey_id = ? ORDER BY question_index', [id]
        );
        const questionIds = questions.map(q => q.id);
        const options = questionIds.length
            ? await allAsync(
                  `SELECT * FROM options WHERE question_id IN (${questionIds.map(() => '?').join(',')}) ORDER BY question_id, option_index`,
                  questionIds
              )
            : [];
        const messages = await allAsync(
            'SELECT * FROM survey_messages WHERE survey_id = ? ORDER BY message_key', [id]
        );
        const testConfigs = await allAsync(
            'SELECT * FROM test_configurations WHERE survey_id = ? ORDER BY display_order', [id]
        );

        const bundle = {
            schema_version: SCHEMA_VERSION,
            exported_at: new Date().toISOString(),
            source: {
                survey_name: survey.name,
                survey_version: survey.version,
                host: req.get('host') || null
            },
            survey,
            sections,
            questions,
            options,
            survey_messages: messages,
            test_configurations: testConfigs
        };

        const filename = `salt-survey_${safeFilename(survey.name)}_v${survey.version}_${new Date().toISOString().slice(0, 10)}.json`;

        await logAudit(
            req.user ? req.user.id : null,
            'EXPORT_SURVEY',
            'survey',
            String(id),
            null,
            { survey_id: id, name: survey.name, version: survey.version,
              questions: questions.length, messages: messages.length }
        );

        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
        res.send(JSON.stringify(bundle, null, 2));
    } catch (err) {
        console.error('Survey export error:', err);
        res.status(500).json({ status: 'error', message: 'Failed to export survey' });
    }
});

router.post('/admin/surveys/import', requireAdmin, async (req, res) => {
    const bundle = req.body;
    if (!bundle || typeof bundle !== 'object') {
        return res.status(400).json({ status: 'error', message: 'Body must be a survey export JSON object' });
    }
    if (bundle.schema_version !== SCHEMA_VERSION) {
        return res.status(400).json({
            status: 'error',
            message: `Unsupported schema_version ${bundle.schema_version} — this server expects ${SCHEMA_VERSION}.`
        });
    }
    const src = bundle.survey;
    if (!src || typeof src !== 'object' || !src.name) {
        return res.status(400).json({ status: 'error', message: 'Bundle is missing the survey object' });
    }
    const srcQuestions = Array.isArray(bundle.questions) ? bundle.questions : [];
    if (srcQuestions.length === 0) {
        return res.status(400).json({ status: 'error', message: 'Bundle has no questions' });
    }
    const srcSections = Array.isArray(bundle.sections) ? bundle.sections : [];
    const srcOptions = Array.isArray(bundle.options) ? bundle.options : [];
    const srcMessages = Array.isArray(bundle.survey_messages) ? bundle.survey_messages : [];
    const srcTestConfigs = Array.isArray(bundle.test_configurations) ? bundle.test_configurations : [];

    const warnings = [];

    // Determine the next version number for this survey name so re-importing
    // doesn't collide with an existing (name, version) tuple already on disk.
    let importVersion = src.version || 1;
    const existing = await allAsync(
        'SELECT version FROM surveys WHERE name = ? ORDER BY version DESC', [src.name]
    );
    if (existing.length) {
        importVersion = (existing[0].version || 0) + 1;
        warnings.push(`Survey named "${src.name}" already exists; imported as version ${importVersion}.`);
    }

    // Warn if the bundle's test_configurations reference rapid test IDs the
    // target catalog doesn't know about (the bundle stores test_id as a
    // string; we just look them up to surface drift).
    if (srcTestConfigs.length) {
        const bundleTestIds = srcTestConfigs.map(t => t.test_id).filter(Boolean);
        // No global test catalog table on the server — test ids live only in
        // test_configurations + on the tablet. We just pass them through and
        // note them in warnings so the admin can sanity-check.
        warnings.push(`Imported ${bundleTestIds.length} rapid test configuration(s); verify they match the tablet build's rapid test ids.`);
    }

    // Lab tests are global; flag that they're not part of the bundle.
    warnings.push('Lab test configurations are global to this deployment and were NOT included in the bundle — configure them via the Lab Tests admin if needed.');

    try {
        await runAsync('BEGIN');

        // Build the survey row, forcing defaults that matter:
        //   - new survey: not active, draft
        //   - version: collision-avoiding (see above)
        //   - parent/base ids dropped to avoid cross-deployment dangling FKs
        const surveyCols = SURVEY_COLUMNS.slice();
        const surveyVals = surveyCols.map(col => {
            switch (col) {
                case 'is_active': return 0;
                case 'is_draft': return 1;
                case 'version': return importVersion;
                case 'base_survey_id':
                case 'parent_survey_id': return null;
                default:
                    return col in src ? src[col] : null;
            }
        });
        const placeholders = surveyCols.map(() => '?').join(',');
        const surveyInsert = await runAsync(
            `INSERT INTO surveys (${surveyCols.join(',')}) VALUES (${placeholders})`,
            surveyVals
        );
        const newSurveyId = surveyInsert.id;

        // Sections — remap section ids.
        const sectionIdMap = new Map();
        for (const s of srcSections) {
            const r = await runAsync(
                `INSERT INTO sections (survey_id, section_index, section_type, name, description)
                 VALUES (?, ?, ?, ?, ?)`,
                [newSurveyId, s.section_index, s.section_type, s.name, s.description || null]
            );
            sectionIdMap.set(s.id, r.id);
        }

        // Questions — remap survey_id + section_id.
        const questionIdMap = new Map();
        for (const q of srcQuestions) {
            const remappedSection = q.section_id != null ? sectionIdMap.get(q.section_id) || null : null;
            if (q.section_id != null && remappedSection == null) {
                warnings.push(`Question "${q.short_name}" referenced section_id ${q.section_id} that wasn't in the bundle; section reference dropped.`);
            }
            const r = await runAsync(
                `INSERT INTO questions (
                    survey_id, question_index, short_name, question_text_json,
                    audio_files_json, question_type, validation_script,
                    validation_error_json, pre_script, section_id,
                    min_selections, max_selections, skip_to_script, skip_to_target
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                    newSurveyId, q.question_index, q.short_name, q.question_text_json,
                    q.audio_files_json || null, q.question_type, q.validation_script || null,
                    q.validation_error_json || null, q.pre_script || null, remappedSection,
                    q.min_selections == null ? null : q.min_selections,
                    q.max_selections == null ? null : q.max_selections,
                    q.skip_to_script || null, q.skip_to_target || null
                ]
            );
            questionIdMap.set(q.id, r.id);
        }

        // Options — remap question_id.
        for (const o of srcOptions) {
            const remappedQuestion = questionIdMap.get(o.question_id);
            if (!remappedQuestion) continue; // skip orphan option
            await runAsync(
                `INSERT INTO options (question_id, option_index, option_text_json, audio_files_json, option_value)
                 VALUES (?, ?, ?, ?, ?)`,
                [remappedQuestion, o.option_index, o.option_text_json, o.audio_files_json || null, o.option_value || null]
            );
        }

        // Survey messages — remap survey_id.
        for (const m of srcMessages) {
            await runAsync(
                `INSERT INTO survey_messages
                    (survey_id, message_key, display_order, message_text_json, audio_files_json, message_type)
                 VALUES (?, ?, ?, ?, ?, ?)`,
                [newSurveyId, m.message_key, m.display_order || 0,
                 m.message_text_json, m.audio_files_json || '{}', m.message_type || 'system']
            );
        }

        // Test configurations — remap survey_id.
        for (const t of srcTestConfigs) {
            await runAsync(
                `INSERT INTO test_configurations (survey_id, test_id, test_name, enabled, display_order)
                 VALUES (?, ?, ?, ?, ?)`,
                [newSurveyId, t.test_id, t.test_name, t.enabled ? 1 : 0, t.display_order || 0]
            );
        }

        await runAsync('COMMIT');

        await logAudit(
            req.user ? req.user.id : null,
            'IMPORT_SURVEY',
            'survey',
            String(newSurveyId),
            null,
            { name: src.name, version: importVersion,
              questions: srcQuestions.length, sections: srcSections.length,
              messages: srcMessages.length, test_configurations: srcTestConfigs.length,
              source_version: src.version }
        );

        res.json({
            status: 'success',
            surveyId: newSurveyId,
            name: src.name,
            version: importVersion,
            counts: {
                sections: srcSections.length,
                questions: srcQuestions.length,
                options: srcOptions.length,
                survey_messages: srcMessages.length,
                test_configurations: srcTestConfigs.length
            },
            warnings
        });
    } catch (err) {
        try { await runAsync('ROLLBACK'); } catch (_) { /* ignore */ }
        console.error('Survey import error:', err);
        res.status(500).json({ status: 'error', message: 'Failed to import survey: ' + (err.message || err) });
    }
});

module.exports = router;
