const express = require('express');
const router = express.Router();
const { requireAuth } = require('../../middleware/auth');
const { allAsync } = require('../../models/database');

/**
 * Display data export page
 * GET /export
 */
router.get('/export', requireAuth, async (req, res) => {
    let surveys = [];
    try {
        surveys = await allAsync('SELECT id, name, version, is_active FROM surveys ORDER BY is_active DESC, id DESC');
    } catch (e) {
        console.error('Failed to load surveys for export page:', e);
    }
    res.render('pages/dataExport', {
        title: 'Data Export',
        user: req.user,
        surveys
    });
});

module.exports = router;