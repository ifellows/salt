const express = require('express');
const router = express.Router();
const { requireAdmin } = require('../../middleware/auth');
const { allAsync, getAsync } = require('../../models/database');

// Lab test management main page
router.get('/admin/lab-tests', requireAdmin, async (req, res) => {
    try {
        const tests = await allAsync(`
            SELECT lt.*,
                   COUNT(lr.id) as result_count
            FROM lab_test_configurations lt
            LEFT JOIN lab_results lr ON lt.id = lr.test_id
            GROUP BY lt.id
            ORDER BY lt.display_order ASC, lt.test_name ASC
        `);

        // Parse options for dropdown tests
        const enhancedTests = tests.map(test => ({
            ...test,
            options: test.options ? JSON.parse(test.options) : null
        }));

        res.render('pages/labTestManagement', {
            title: 'Lab Test Configuration',
            tests: enhancedTests,
            user: req.user,
            success: req.query.success,
            error: req.query.error
        });
    } catch (error) {
        console.error('Error loading lab test management page:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load lab test management page'
        });
    }
});

// Create/edit lab test form
router.get('/admin/lab-tests/new', requireAdmin, (req, res) => {
    res.render('pages/labTestForm', {
        title: 'Create New Lab Test',
        user: req.user,
        editTest: null,
        error: req.query.error
    });
});

// Edit lab test form
router.get('/admin/lab-tests/:id/edit', requireAdmin, async (req, res) => {
    try {
        const testId = req.params.id;
        const editTest = await getAsync(
            'SELECT * FROM lab_test_configurations WHERE id = ?',
            [testId]
        );

        if (!editTest) {
            return res.redirect('/admin/lab-tests?error=' + encodeURIComponent('Test not found'));
        }

        // Parse options if it's a dropdown test
        if (editTest.options) {
            editTest.options = JSON.parse(editTest.options);
        }

        res.render('pages/labTestForm', {
            title: 'Edit Lab Test',
            user: req.user,
            editTest: editTest,
            error: req.query.error
        });
    } catch (error) {
        console.error('Error loading lab test edit form:', error);
        res.redirect('/admin/lab-tests?error=' + encodeURIComponent('Failed to load test'));
    }
});

module.exports = router;