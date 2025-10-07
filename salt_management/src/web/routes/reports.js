const express = require('express');
const router = express.Router();
const { requireAdmin } = require('../../middleware/auth');
const { getAsync } = require('../../models/database');

/**
 * Reports list page
 * GET /reports
 */
router.get('/reports', requireAdmin, (req, res) => {
    res.render('pages/reports', {
        title: 'Reports',
        username: req.session.username
    });
});

/**
 * New report page
 * GET /reports/new
 */
router.get('/reports/new', requireAdmin, (req, res) => {
    res.render('pages/reportEditor', {
        title: 'Create Report',
        username: req.session.username,
        id: null,
        report: null
    });
});

/**
 * Edit report page
 * GET /reports/:id/edit
 */
router.get('/reports/:id/edit', requireAdmin, async (req, res) => {
    try {
        const report = await getAsync(
            'SELECT * FROM reports WHERE id = ?',
            [req.params.id]
        );

        if (!report) {
            return res.status(404).render('pages/error', {
                title: 'Error',
                message: 'Report not found',
                username: req.session.username
            });
        }

        res.render('pages/reportEditor', {
            title: 'Edit Report',
            username: req.session.username,
            id: report.id,
            report: report
        });
    } catch (error) {
        console.error('Failed to load report for editing:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load report',
            username: req.session.username
        });
    }
});

/**
 * Report history page
 * GET /reports/:id/history
 */
router.get('/reports/:id/history', requireAdmin, async (req, res) => {
    try {
        const report = await getAsync(
            'SELECT name FROM reports WHERE id = ?',
            [req.params.id]
        );

        if (!report) {
            return res.status(404).render('pages/error', {
                title: 'Error',
                message: 'Report not found',
                username: req.session.username
            });
        }

        res.render('pages/reportHistory', {
            title: 'Report History',
            username: req.session.username,
            id: req.params.id,
            name: report.name
        });
    } catch (error) {
        console.error('Failed to load report history:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load report history',
            username: req.session.username
        });
    }
});

module.exports = router;