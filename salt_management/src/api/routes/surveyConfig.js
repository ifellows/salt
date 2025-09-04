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
        
        // Get questions with options
        const questions = await allAsync(
            'SELECT * FROM questions WHERE survey_id = ? ORDER BY question_index',
            [req.params.id]
        );
        
        // Get options for each question
        const questionsWithOptions = await Promise.all(
            questions.map(async (question) => {
                const options = await allAsync(
                    'SELECT * FROM options WHERE question_id = ? ORDER BY option_index',
                    [question.id]
                );
                return { ...question, options };
            })
        );
        
        res.json({ ...survey, questions: questionsWithOptions });
    } catch (error) {
        console.error('Error fetching survey:', error);
        res.status(500).json({ error: 'Failed to fetch survey' });
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
        
        // Create new survey
        const result = await runAsync(
            'INSERT INTO surveys (version, name, description) VALUES (?, ?, ?)',
            [newVersion, name, description]
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
                    `INSERT INTO questions (survey_id, question_index, short_name, question_text, 
                     question_type, validation_script, pre_script, audio_file)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                    [newSurveyId, question.question_index, question.short_name, 
                     question.question_text, question.question_type, 
                     question.validation_script, question.pre_script, question.audio_file]
                );
                
                // Copy options for this question
                const options = await allAsync(
                    'SELECT * FROM options WHERE question_id = ?',
                    [question.id]
                );
                
                for (const option of options) {
                    await runAsync(
                        `INSERT INTO options (question_id, option_index, option_text, option_value, audio_file)
                         VALUES (?, ?, ?, ?, ?)`,
                        [questionResult.id, option.option_index, option.option_text, 
                         option.option_value, option.audio_file]
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

// Update survey metadata
router.put('/:id', [
    body('name').optional().trim(),
    body('description').optional().trim()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { name, description } = req.body;
    const surveyId = req.params.id;
    
    try {
        const oldSurvey = await getAsync(
            'SELECT * FROM surveys WHERE id = ?',
            [surveyId]
        );
        
        if (!oldSurvey) {
            return res.status(404).json({ error: 'Survey not found' });
        }
        
        await runAsync(
            `UPDATE surveys 
             SET name = COALESCE(?, name), 
                 description = COALESCE(?, description),
                 updated_at = CURRENT_TIMESTAMP
             WHERE id = ?`,
            [name, description, surveyId]
        );
        
        await logAudit(
            req.session.userId,
            'UPDATE_SURVEY',
            'survey',
            surveyId,
            oldSurvey,
            { name: name || oldSurvey.name, description: description || oldSurvey.description }
        );
        
        res.json({ message: 'Survey updated successfully' });
    } catch (error) {
        console.error('Error updating survey:', error);
        res.status(500).json({ error: 'Failed to update survey' });
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

// Add/Update question
router.post('/:surveyId/questions', [
    body('question_index').isInt(),
    body('short_name').notEmpty().trim().matches(/^[a-zA-Z0-9_]+$/).withMessage('Short name can only contain letters, numbers, and underscores'),
    body('question_text').notEmpty().trim(),
    body('primary_language_text').optional().trim(),
    body('question_type').isIn(['multiple_choice', 'numeric', 'text', 'date']),
    body('validation_script').optional().trim(),
    body('validation_error_text').optional().trim(),
    body('pre_script').optional().trim(),
    body('audio_file').optional().trim(),
    body('options').optional().isArray()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const surveyId = req.params.surveyId;
    const { 
        question_index, short_name, question_text, primary_language_text, question_type,
        validation_script, validation_error_text, pre_script, audio_file, options = []
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
            `INSERT INTO questions (survey_id, question_index, short_name, question_text, primary_language_text,
             question_type, validation_script, validation_error_text, pre_script, audio_file)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [surveyId, question_index, short_name, question_text, primary_language_text,
             question_type, validation_script, validation_error_text || 'Invalid answer', pre_script, audio_file]
        );
        
        const questionId = questionResult.id;
        
        // Insert options if provided
        for (let i = 0; i < options.length; i++) {
            const option = options[i];
            await runAsync(
                `INSERT INTO options (question_id, option_index, option_text, primary_language_text, option_value, audio_file)
                 VALUES (?, ?, ?, ?, ?, ?)`,
                [questionId, option.option_index !== undefined ? option.option_index : i, 
                 option.text, option.primary_language_text || null,
                 option.value || option.text, option.audio_file || null]
            );
        }
        
        await logAudit(
            req.session.userId,
            'ADD_QUESTION',
            'question',
            questionId,
            null,
            { surveyId, short_name, question_text }
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

// Update question
router.put('/:surveyId/questions/:questionId', [
    body('question_index').optional().isInt(),
    body('short_name').optional().trim().matches(/^[a-zA-Z0-9_]+$/).withMessage('Short name can only contain letters, numbers, and underscores'),
    body('question_text').optional().trim(),
    body('question_type').optional().isIn(['multiple_choice', 'numeric', 'text', 'date']),
    body('validation_script').optional().trim(),
    body('pre_script').optional().trim(),
    body('audio_file').optional().trim(),
    body('options').optional().isArray()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { surveyId, questionId } = req.params;
    const { 
        question_index, short_name, question_text, question_type,
        validation_script, pre_script, audio_file, options
    } = req.body;
    
    try {
        // Check question exists and belongs to survey
        const question = await getAsync(
            'SELECT * FROM questions WHERE id = ? AND survey_id = ?',
            [questionId, surveyId]
        );
        
        if (!question) {
            return res.status(404).json({ error: 'Question not found' });
        }
        
        // Update question
        await runAsync(
            `UPDATE questions 
             SET question_index = COALESCE(?, question_index),
                 short_name = COALESCE(?, short_name),
                 question_text = COALESCE(?, question_text),
                 question_type = COALESCE(?, question_type),
                 validation_script = COALESCE(?, validation_script),
                 pre_script = COALESCE(?, pre_script),
                 audio_file = COALESCE(?, audio_file)
             WHERE id = ?`,
            [question_index, short_name, question_text, question_type,
             validation_script, pre_script, audio_file, questionId]
        );
        
        // Update options if provided
        if (options) {
            // Delete existing options
            await runAsync('DELETE FROM options WHERE question_id = ?', [questionId]);
            
            // Insert new options
            for (let i = 0; i < options.length; i++) {
                const option = options[i];
                await runAsync(
                    `INSERT INTO options (question_id, option_index, option_text, option_value, audio_file)
                     VALUES (?, ?, ?, ?, ?)`,
                    [questionId, i, option.text, option.value || option.text, option.audio_file]
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
        
        // Delete options first (due to foreign key)
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
    const { questionOrder } = req.body; // Array of question IDs in new order
    
    try {
        // Update each question's index
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

module.exports = router;