const express = require('express');
const { body, validationResult } = require('express-validator');
const { getAsync, runAsync, allAsync } = require('../../models/database');
const { requireAdmin } = require('../middleware/auth');
const { logAudit } = require('../../services/auditService');
const router = express.Router();

// All routes require admin authentication
router.use(requireAdmin);

// List all survey versions
router.get('/', async (req, res) => {
    try {
        const surveys = await allAsync(
            `SELECT s.*, 
                    COUNT(DISTINCT q.id) as question_count
             FROM surveys s
             LEFT JOIN questions q ON s.id = q.survey_id
             GROUP BY s.id
             ORDER BY s.version DESC`
        );
        res.json(surveys);
    } catch (error) {
        console.error('Error fetching surveys:', error);
        res.status(500).json({ error: 'Failed to fetch surveys' });
    }
});

// Get single survey with all questions and options
router.get('/:id', async (req, res) => {
    try {
        const survey = await getAsync(
            'SELECT * FROM surveys WHERE id = ?',
            [req.params.id]
        );
        
        if (!survey) {
            return res.status(404).json({ error: 'Survey not found' });
        }
        
        // Parse languages if stored as JSON string
        if (survey.languages && typeof survey.languages === 'string') {
            survey.languages = JSON.parse(survey.languages);
        }
        
        // Get questions with options
        const questions = await allAsync(
            'SELECT * FROM questions WHERE survey_id = ? ORDER BY question_index',
            [req.params.id]
        );
        
        // Get options for each question and parse JSON fields
        const questionsWithOptions = await Promise.all(
            questions.map(async (question) => {
                // Parse JSON fields
                if (question.question_text_json) {
                    question.question_text_json = JSON.parse(question.question_text_json);
                }
                if (question.audio_files_json) {
                    question.audio_files_json = JSON.parse(question.audio_files_json);
                }
                if (question.validation_error_json) {
                    question.validation_error_json = JSON.parse(question.validation_error_json);
                }
                
                const options = await allAsync(
                    'SELECT * FROM options WHERE question_id = ? ORDER BY option_index',
                    [question.id]
                );
                
                // Parse JSON fields in options
                options.forEach(opt => {
                    if (opt.option_text_json) {
                        opt.option_text_json = JSON.parse(opt.option_text_json);
                    }
                    if (opt.audio_files_json) {
                        opt.audio_files_json = JSON.parse(opt.audio_files_json);
                    }
                });
                
                return { ...question, options };
            })
        );
        
        res.json({ ...survey, questions: questionsWithOptions });
    } catch (error) {
        console.error('Error fetching survey:', error);
        res.status(500).json({ error: 'Failed to fetch survey' });
    }
});

// Update survey (including languages)
router.put('/:id', [
    body('name').optional().trim(),
    body('description').optional().trim(),
    body('languages').optional()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { name, description, languages } = req.body;
    const surveyId = req.params.id;
    
    try {
        const oldSurvey = await getAsync(
            'SELECT * FROM surveys WHERE id = ?',
            [surveyId]
        );
        
        if (!oldSurvey) {
            return res.status(404).json({ error: 'Survey not found' });
        }
        
        // Build update query
        const updates = [];
        const params = [];
        
        if (name !== undefined) {
            updates.push('name = ?');
            params.push(name);
        }
        if (description !== undefined) {
            updates.push('description = ?');
            params.push(description);
        }
        if (languages !== undefined) {
            updates.push('languages = ?');
            params.push(languages);
        }
        
        if (updates.length > 0) {
            updates.push('updated_at = CURRENT_TIMESTAMP');
            params.push(surveyId);
            
            await runAsync(
                `UPDATE surveys SET ${updates.join(', ')} WHERE id = ?`,
                params
            );
        }
        
        await logAudit(
            req.session.userId,
            'UPDATE_SURVEY',
            'survey',
            surveyId,
            oldSurvey,
            { name, description, languages }
        );
        
        res.json({ message: 'Survey updated successfully' });
    } catch (error) {
        console.error('Error updating survey:', error);
        res.status(500).json({ error: 'Failed to update survey' });
    }
});

// Add question with multi-language support
router.post('/:surveyId/questions', [
    body('question_index').isInt(),
    body('short_name').notEmpty().trim().matches(/^[a-zA-Z0-9_]+$/),
    body('question_text_json').isObject(),
    body('audio_files_json').optional().isObject(),
    body('question_type').isIn(['multiple_choice', 'numeric', 'text']),
    body('validation_script').optional().trim(),
    body('validation_error_json').optional().isObject(),
    body('pre_script').optional().trim(),
    body('options').optional().isArray()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const surveyId = req.params.surveyId;
    const { 
        question_index, short_name, question_text_json, audio_files_json,
        question_type, validation_script, validation_error_json, pre_script, options = []
    } = req.body;
    
    try {
        // Check survey exists
        const survey = await getAsync(
            'SELECT * FROM surveys WHERE id = ?',
            [surveyId]
        );
        
        if (!survey) {
            return res.status(404).json({ error: 'Survey not found' });
        }
        
        // Insert question
        const questionResult = await runAsync(
            `INSERT INTO questions (survey_id, question_index, short_name, question_text_json,
             audio_files_json, question_type, validation_script, validation_error_json, pre_script)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [surveyId, question_index, short_name, JSON.stringify(question_text_json),
             JSON.stringify(audio_files_json || {}), question_type, validation_script,
             JSON.stringify(validation_error_json || {"en": "Invalid answer"}), pre_script]
        );
        
        const questionId = questionResult.id;
        
        // Insert options if provided
        for (const option of options) {
            await runAsync(
                `INSERT INTO options (question_id, option_index, option_text_json, audio_files_json, option_value)
                 VALUES (?, ?, ?, ?, ?)`,
                [questionId, option.option_index || 0,
                 JSON.stringify(option.text_json || {}),
                 JSON.stringify(option.audio_json || {}),
                 option.value || JSON.stringify(option.text_json)]
            );
        }
        
        await logAudit(
            req.session.userId,
            'ADD_QUESTION',
            'question',
            questionId,
            null,
            { surveyId, short_name, question_text_json }
        );
        
        res.status(201).json({
            id: questionId,
            message: 'Question added successfully'
        });
    } catch (error) {
        console.error('Error adding question:', error);
        res.status(500).json({ error: 'Failed to add question' });
    }
});

// Update question with multi-language support
router.put('/:surveyId/questions/:questionId', [
    body('question_index').optional().isInt(),
    body('short_name').optional().trim().matches(/^[a-zA-Z0-9_]+$/),
    body('question_text_json').optional().isObject(),
    body('audio_files_json').optional().isObject(),
    body('question_type').optional().isIn(['multiple_choice', 'numeric', 'text']),
    body('validation_script').optional().trim(),
    body('validation_error_json').optional().isObject(),
    body('pre_script').optional().trim(),
    body('options').optional().isArray()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { surveyId, questionId } = req.params;
    const { 
        question_index, short_name, question_text_json, audio_files_json,
        question_type, validation_script, validation_error_json, pre_script, options
    } = req.body;
    
    try {
        // Check question exists
        const question = await getAsync(
            'SELECT * FROM questions WHERE id = ? AND survey_id = ?',
            [questionId, surveyId]
        );
        
        if (!question) {
            return res.status(404).json({ error: 'Question not found' });
        }
        
        // Build update query
        const updates = [];
        const params = [];
        
        if (question_index !== undefined) {
            updates.push('question_index = ?');
            params.push(question_index);
        }
        if (short_name !== undefined) {
            updates.push('short_name = ?');
            params.push(short_name);
        }
        if (question_text_json !== undefined) {
            updates.push('question_text_json = ?');
            params.push(JSON.stringify(question_text_json));
        }
        if (audio_files_json !== undefined) {
            updates.push('audio_files_json = ?');
            params.push(JSON.stringify(audio_files_json));
        }
        if (question_type !== undefined) {
            updates.push('question_type = ?');
            params.push(question_type);
        }
        if (validation_script !== undefined) {
            updates.push('validation_script = ?');
            params.push(validation_script);
        }
        if (validation_error_json !== undefined) {
            updates.push('validation_error_json = ?');
            params.push(JSON.stringify(validation_error_json));
        }
        if (pre_script !== undefined) {
            updates.push('pre_script = ?');
            params.push(pre_script);
        }
        
        if (updates.length > 0) {
            params.push(questionId);
            await runAsync(
                `UPDATE questions SET ${updates.join(', ')} WHERE id = ?`,
                params
            );
        }
        
        // Update options if provided
        if (options !== undefined) {
            // Delete existing options
            await runAsync('DELETE FROM options WHERE question_id = ?', [questionId]);
            
            // Insert new options
            for (const option of options) {
                await runAsync(
                    `INSERT INTO options (question_id, option_index, option_text_json, audio_files_json, option_value)
                     VALUES (?, ?, ?, ?, ?)`,
                    [questionId, option.option_index || 0,
                     JSON.stringify(option.text_json || {}),
                     JSON.stringify(option.audio_json || {}),
                     option.value || JSON.stringify(option.text_json)]
                );
            }
        }
        
        await logAudit(
            req.session.userId,
            'UPDATE_QUESTION',
            'question',
            questionId,
            question,
            req.body
        );
        
        res.json({ message: 'Question updated successfully' });
    } catch (error) {
        console.error('Error updating question:', error);
        res.status(500).json({ error: 'Failed to update question' });
    }
});

// Delete question
router.delete('/:surveyId/questions/:questionId', async (req, res) => {
    const { surveyId, questionId } = req.params;
    
    try {
        const question = await getAsync(
            'SELECT * FROM questions WHERE id = ? AND survey_id = ?',
            [questionId, surveyId]
        );
        
        if (!question) {
            return res.status(404).json({ error: 'Question not found' });
        }
        
        // Delete options first
        await runAsync('DELETE FROM options WHERE question_id = ?', [questionId]);
        
        // Delete question
        await runAsync('DELETE FROM questions WHERE id = ?', [questionId]);
        
        await logAudit(
            req.session.userId,
            'DELETE_QUESTION',
            'question',
            questionId,
            question,
            null
        );
        
        res.json({ message: 'Question deleted successfully' });
    } catch (error) {
        console.error('Error deleting question:', error);
        res.status(500).json({ error: 'Failed to delete question' });
    }
});

// Reorder questions
router.post('/:surveyId/reorder', [
    body('questionOrder').isArray()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { surveyId } = req.params;
    const { questionOrder } = req.body;
    
    try {
        for (let i = 0; i < questionOrder.length; i++) {
            await runAsync(
                'UPDATE questions SET question_index = ? WHERE id = ? AND survey_id = ?',
                [i, questionOrder[i], surveyId]
            );
        }
        
        await logAudit(
            req.session.userId,
            'REORDER_QUESTIONS',
            'survey',
            surveyId,
            null,
            { questionOrder }
        );
        
        res.json({ message: 'Questions reordered successfully' });
    } catch (error) {
        console.error('Error reordering questions:', error);
        res.status(500).json({ error: 'Failed to reorder questions' });
    }
});

// Create new survey version
router.post('/', [
    body('name').notEmpty().trim(),
    body('description').optional().trim(),
    body('basedOnSurveyId').optional().isInt()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { name, description, basedOnSurveyId } = req.body;
    
    try {
        // Get next version number
        const latestVersion = await getAsync(
            'SELECT MAX(version) as max_version FROM surveys'
        );
        const newVersion = (latestVersion?.max_version || 0) + 1;
        
        // Get languages from base survey or use default
        let languages = '["en"]';
        if (basedOnSurveyId) {
            const baseSurvey = await getAsync(
                'SELECT languages FROM surveys WHERE id = ?',
                [basedOnSurveyId]
            );
            if (baseSurvey && baseSurvey.languages) {
                languages = baseSurvey.languages;
            }
        }
        
        // Create new survey
        const result = await runAsync(
            'INSERT INTO surveys (version, name, description, languages) VALUES (?, ?, ?, ?)',
            [newVersion, name, description, languages]
        );
        
        const newSurveyId = result.id;
        
        // If based on existing survey, copy questions and options
        if (basedOnSurveyId) {
            const questions = await allAsync(
                'SELECT * FROM questions WHERE survey_id = ?',
                [basedOnSurveyId]
            );
            
            for (const question of questions) {
                const questionResult = await runAsync(
                    `INSERT INTO questions (survey_id, question_index, short_name, question_text_json, 
                     audio_files_json, question_type, validation_script, validation_error_json, pre_script)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
                    [newSurveyId, question.question_index, question.short_name, 
                     question.question_text_json, question.audio_files_json,
                     question.question_type, question.validation_script, 
                     question.validation_error_json, question.pre_script]
                );
                
                // Copy options for this question
                const options = await allAsync(
                    'SELECT * FROM options WHERE question_id = ?',
                    [question.id]
                );
                
                for (const option of options) {
                    await runAsync(
                        `INSERT INTO options (question_id, option_index, option_text_json, audio_files_json, option_value)
                         VALUES (?, ?, ?, ?, ?)`,
                        [questionResult.id, option.option_index, option.option_text_json, 
                         option.audio_files_json, option.option_value]
                    );
                }
            }
        }
        
        await logAudit(
            req.session.userId,
            'CREATE_SURVEY',
            'survey',
            newSurveyId,
            null,
            { name, description, version: newVersion }
        );
        
        res.status(201).json({
            id: newSurveyId,
            version: newVersion,
            name,
            description,
            message: 'Survey created successfully'
        });
    } catch (error) {
        console.error('Error creating survey:', error);
        res.status(500).json({ error: 'Failed to create survey' });
    }
});

// Activate a survey version (deactivates all others)
router.post('/:id/activate', async (req, res) => {
    const surveyId = req.params.id;
    
    try {
        const survey = await getAsync(
            'SELECT * FROM surveys WHERE id = ?',
            [surveyId]
        );
        
        if (!survey) {
            return res.status(404).json({ error: 'Survey not found' });
        }
        
        // Deactivate all surveys
        await runAsync('UPDATE surveys SET is_active = 0');
        
        // Activate this survey
        await runAsync(
            'UPDATE surveys SET is_active = 1 WHERE id = ?',
            [surveyId]
        );
        
        await logAudit(
            req.session.userId,
            'ACTIVATE_SURVEY',
            'survey',
            surveyId,
            null,
            { version: survey.version, name: survey.name }
        );
        
        res.json({ message: 'Survey activated successfully' });
    } catch (error) {
        console.error('Error activating survey:', error);
        res.status(500).json({ error: 'Failed to activate survey' });
    }
});

module.exports = router;