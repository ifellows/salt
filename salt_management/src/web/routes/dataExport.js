const express = require('express');
const router = express.Router();
const { requireAuth } = require('../../middleware/auth');

/**
 * Display data export page
 * GET /export
 */
router.get('/export', requireAuth, (req, res) => {
    res.render('pages/dataExport', {
        title: 'Data Export',
        user: req.user
    });
});

module.exports = router;