const express = require('express');
const { requireLabStaff, requireStaffOrAdmin } = require('../../middleware/auth');
const { getAsync, allAsync } = require('../../models/database');
const router = express.Router();

// Lab entry main page (accessible by lab_staff and admin)
router.get('/lab-entry', requireStaffOrAdmin, async (req, res) => {
    try {
        res.render('pages/labEntry', {
            title: 'Laboratory Results Entry',
            user: req.user
        });
    } catch (error) {
        console.error('Lab entry page error:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load lab entry page'
        });
    }
});

module.exports = router;