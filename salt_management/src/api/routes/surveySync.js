const express = require('express');
const crypto = require('crypto');
const { getAsync, allAsync } = require('../../models/database');
const { requireFacilityApiKey } = require('../../web/middleware/auth');
const router = express.Router();

/**
 * Calculate checksum for survey content
 * @param {Object} survey - Survey data including questions and options
 * @returns {String} SHA256 hash of the content
 */
function calculateChecksum(survey) {
    const content = JSON.stringify({
        survey: survey.survey,
        sections: survey.sections,
        questions: survey.questions,
        options: survey.options,
        messages: survey.messages || []  // Include messages in checksum
    });


    return crypto.createHash('sha256').update(content).digest('hex');
}

/**
 * Get current active survey version
 * Used by Android app to check if update is needed
 */
router.get('/survey/version', requireFacilityApiKey, async (req, res) => {
    try {
        // Get the full active survey
        const activeSurvey = await getAsync(
            `SELECT * FROM surveys
             WHERE is_active = 1
             LIMIT 1`
        );

        if (!activeSurvey) {
            return res.status(404).json({
                status: 'error',
                message: 'No active survey found'
            });
        }

        // Get sections
        const sections = await allAsync(
            'SELECT * FROM sections WHERE survey_id = ? ORDER BY section_index',
            [activeSurvey.id]
        );

        // Get all questions
        const questions = await allAsync(
            'SELECT * FROM questions WHERE survey_id = ? ORDER BY question_index',
            [activeSurvey.id]
        );

        // Get all options
        const options = [];
        for (const question of questions) {
            const questionOptions = await allAsync(
                'SELECT * FROM options WHERE question_id = ? ORDER BY option_index',
                [question.id]
            );
            options.push(...questionOptions);
        }

        // Get all messages for the survey
        const messages = await allAsync(
            `SELECT message_key, message_text_json, audio_files_json, message_type, display_order
             FROM survey_messages
             WHERE survey_id = ?
             ORDER BY display_order, message_key`,
            [activeSurvey.id]
        );

        // Calculate checksum from actual content (same as download endpoint)
        const contentForChecksum = {
            survey: activeSurvey,  // Use full survey object
            sections: sections,
            questions: questions,
            options: options,
            messages: messages
        };

        const checksum = calculateChecksum(contentForChecksum);

        // Get the latest modification time from related tables
        const messageUpdate = await getAsync(
            'SELECT MAX(updated_at) as updated_at FROM survey_messages WHERE survey_id = ?',
            [activeSurvey.id]
        );

        // Use the most recent update time
        const lastUpdated = Math.max(
            new Date(activeSurvey.updated_at).getTime(),
            messageUpdate?.updated_at ? new Date(messageUpdate.updated_at).getTime() : 0
        );

        res.json({
            status: 'success',
            data: {
                survey_id: activeSurvey.id,
                version: activeSurvey.version,
                base_survey_id: activeSurvey.base_survey_id,
                name: activeSurvey.name,
                checksum: checksum,  // Now using full content-based checksum
                updated_at: activeSurvey.updated_at,
                last_modified: lastUpdated, // Timestamp in milliseconds
                is_mandatory_update: false,
                min_app_version: '1.0.0'
            }
        });

    } catch (error) {
        console.error('Error checking survey version:', error);
        res.status(500).json({
            status: 'error',
            message: 'Failed to check survey version'
        });
    }
});

/**
 * Download complete active survey
 * Returns all survey data needed by Android app
 */
router.get('/survey/download', requireFacilityApiKey, async (req, res) => {
    try {
        // Get the full active survey
        const survey = await getAsync(
            `SELECT * FROM surveys
             WHERE is_active = 1
             LIMIT 1`
        );
        
        if (!survey) {
            return res.status(404).json({
                status: 'error',
                message: 'No active survey found'
            });
        }
        
        // Parse JSON fields for response
        if (survey.languages && typeof survey.languages === 'string') {
            survey.languages = JSON.parse(survey.languages);
        }
        if (survey.eligibility_message_json && typeof survey.eligibility_message_json === 'string') {
            survey.eligibility_message_json = JSON.parse(survey.eligibility_message_json);
        }
        
        // Get sections
        const sections = await allAsync(
            'SELECT * FROM sections WHERE survey_id = ? ORDER BY section_index',
            [survey.id]
        );
        
        // Get all questions
        const questions = await allAsync(
            'SELECT * FROM questions WHERE survey_id = ? ORDER BY question_index',
            [survey.id]
        );

        // Parse JSON fields in questions (create new objects to avoid mutation)
        const questionsWithParsedJson = questions.map(q => {
            const parsedQ = { ...q };
            if (parsedQ.question_text_json && typeof parsedQ.question_text_json === 'string') {
                parsedQ.question_text_json = JSON.parse(parsedQ.question_text_json);
            }
            if (parsedQ.audio_files_json && typeof parsedQ.audio_files_json === 'string') {
                parsedQ.audio_files_json = JSON.parse(parsedQ.audio_files_json);
            }
            if (parsedQ.validation_error_json && typeof parsedQ.validation_error_json === 'string') {
                parsedQ.validation_error_json = JSON.parse(parsedQ.validation_error_json);
            }
            return parsedQ;
        });
        
        // Get all options
        const options = [];
        for (const question of questions) {
            const questionOptions = await allAsync(
                'SELECT * FROM options WHERE question_id = ? ORDER BY option_index',
                [question.id]
            );

            // Store raw options
            options.push(...questionOptions);
        }

        // Parse JSON fields in options for response
        const optionsWithParsedJson = options.map(opt => {
            const parsedOpt = { ...opt };
            if (parsedOpt.option_text_json && typeof parsedOpt.option_text_json === 'string') {
                parsedOpt.option_text_json = JSON.parse(parsedOpt.option_text_json);
            }
            if (parsedOpt.audio_files_json && typeof parsedOpt.audio_files_json === 'string') {
                parsedOpt.audio_files_json = JSON.parse(parsedOpt.audio_files_json);
            }
            return parsedOpt;
        });

        // Get all messages for the survey
        const messages = await allAsync(
            `SELECT message_key, message_text_json, audio_files_json, message_type, display_order
             FROM survey_messages
             WHERE survey_id = ?
             ORDER BY display_order, message_key`,
            [survey.id]
        );

        // Parse JSON fields in messages
        const messagesWithParsedJson = messages.map(msg => ({
            message_key: msg.message_key,
            message_type: msg.message_type,
            display_order: msg.display_order,
            message_text_json: JSON.parse(msg.message_text_json || '{}'), // Keep original for compatibility
            audio_files_json: JSON.parse(msg.audio_files_json || '{}'), // Keep original for compatibility
            text: JSON.parse(msg.message_text_json || '{}'), // Also provide simplified names
            audio: JSON.parse(msg.audio_files_json || '{}')
        }));

        // Log the messages being sent
        console.log('Survey sync - messages found:', messages.length);
        if (messages.length > 0) {
            console.log('Sample message:', messagesWithParsedJson[0]);
        }
        
        // Log the fingerprint configuration being sent
        console.log('Survey sync - fingerprint config:', {
            fingerprint_enabled: survey.fingerprint_enabled,
            re_enrollment_days: survey.re_enrollment_days,
            survey_name: survey.name
        });
        
        // Prepare response data
        const responseData = {
            survey: {
                id: survey.id,
                version: survey.version,
                base_survey_id: survey.base_survey_id,
                name: survey.name,
                description: survey.description,
                languages: survey.languages,
                eligibility_script: survey.eligibility_script,
                eligibility_message_json: survey.eligibility_message_json,
                staff_validation_message_json: survey.staff_validation_message_json
            },
            survey_config: {
                survey_name: survey.name,
                fingerprint_enabled: Boolean(survey.fingerprint_enabled),
                re_enrollment_days: survey.re_enrollment_days || 90
            },
            sections: sections,
            questions: questionsWithParsedJson,
            options: optionsWithParsedJson,
            messages: messagesWithParsedJson
        };
        
        // Get raw survey for checksum calculation (re-fetch to ensure no mutations)
        const rawSurvey = await getAsync(
            `SELECT * FROM surveys
             WHERE is_active = 1
             LIMIT 1`
        );

        // Calculate checksum using raw survey data (to match version endpoint)
        const checksumData = {
            survey: rawSurvey,  // Use raw survey object (before JSON parsing)
            sections: sections,
            questions: questions,  // Use raw questions, not parsed
            options: options,
            messages: messages  // Use raw messages, not parsed
        };
        const checksum = calculateChecksum(checksumData);
        
        // Add metadata
        responseData.metadata = {
            checksum: checksum,
            version: survey.version,
            generated_at: new Date().toISOString(),
            total_questions: questions.length,
            total_options: options.length,
            compressed: false,
            size_bytes: JSON.stringify(responseData).length
        };
        
        // Set cache headers
        res.set({
            'Cache-Control': 'public, max-age=300', // Cache for 5 minutes
            'ETag': checksum
        });
        
        res.json({
            status: 'success',
            data: responseData
        });
        
    } catch (error) {
        console.error('Error downloading survey:', error);
        res.status(500).json({
            status: 'error',
            message: 'Failed to download survey'
        });
    }
});

/**
 * Get survey changes between versions (optional - for delta updates)
 * Not implemented in Phase 1
 */
router.get('/survey/delta', requireFacilityApiKey, async (req, res) => {
    const { from_version, to_version } = req.query;
    
    // TODO: Implement delta updates in Phase 2
    res.status(501).json({
        status: 'error',
        message: 'Delta updates not yet implemented'
    });
});

/**
 * Report sync status from device (optional - for analytics)
 */
router.post('/survey/sync-status', requireFacilityApiKey, async (req, res) => {
    const { device_id, survey_id, version, sync_result, error_message } = req.body;
    
    // TODO: Log sync status for monitoring
    console.log('Sync status received:', {
        device_id,
        survey_id,
        version,
        sync_result,
        timestamp: new Date().toISOString()
    });
    
    res.json({
        status: 'success',
        message: 'Sync status recorded'
    });
});

module.exports = router;