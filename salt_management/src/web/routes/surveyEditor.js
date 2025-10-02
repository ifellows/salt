const express = require('express');
const { requireAdmin } = require('../../middleware/auth');
const { allAsync, getAsync } = require('../../models/database');
const router = express.Router();

// Survey list page
router.get('/surveys', requireAdmin, async (req, res) => {
    try {
        const surveys = await allAsync(`
            SELECT s.*, 
                   COUNT(DISTINCT q.id) as question_count
            FROM surveys s
            LEFT JOIN questions q ON s.id = q.survey_id
            GROUP BY s.id
            ORDER BY s.version DESC
        `);
        
        res.render('pages/surveys', {
            title: 'Survey Configuration',
            username: req.session.username,
            surveys
        });
    } catch (error) {
        console.error('Survey list error:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load surveys'
        });
    }
});

// Survey editor page
router.get('/surveys/:id/edit', requireAdmin, async (req, res) => {
    try {
        const survey = await getAsync(
            'SELECT * FROM surveys WHERE id = ?',
            [req.params.id]
        );
        
        if (!survey) {
            return res.status(404).render('pages/404', {
                title: 'Survey Not Found'
            });
        }
        
        // Get sections for this survey
        const sections = await allAsync(
            'SELECT * FROM sections WHERE survey_id = ? ORDER BY section_index',
            [req.params.id]
        );
        
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
        
        res.render('pages/surveyEditorSimple', {
            title: `Edit Survey: ${survey.name}`,
            username: req.session.username,
            survey,
            sections,
            questions: questionsWithOptions
        });
    } catch (error) {
        console.error('Survey editor error:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load survey editor'
        });
    }
});

// Create new survey page
router.get('/surveys/new', requireAdmin, async (req, res) => {
    try {
        // Get existing surveys to allow cloning
        const surveys = await allAsync(
            'SELECT id, name, version FROM surveys ORDER BY version DESC'
        );
        
        res.render('pages/newSurvey', {
            title: 'Create New Survey',
            username: req.session.username,
            existingSurveys: surveys
        });
    } catch (error) {
        console.error('New survey page error:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load new survey page'
        });
    }
});

module.exports = router;