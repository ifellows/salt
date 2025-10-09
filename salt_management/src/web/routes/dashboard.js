const express = require('express');
const { requireAdmin } = require('../../middleware/auth');
const { allAsync, getAsync } = require('../../models/database');
const router = express.Router();

// Login page
router.get('/login', (req, res) => {
    if (req.session.userId) {
        return res.redirect('/');
    }
    res.render('pages/login', { title: 'Login', error: null });
});

// Admin redirect
router.get('/admin', requireAdmin, (req, res) => {
    res.redirect('/');
});

// Main dashboard (admin only)
router.get('/', requireAdmin, async (req, res) => {
    try {
        // Get statistics
        const stats = await getAsync(`
            SELECT 
                (SELECT COUNT(*) FROM facilities) as total_facilities,
                (SELECT COUNT(*) FROM uploads) as total_uploads,
                (SELECT COUNT(*) FROM uploads WHERE status = 'completed') as completed_uploads,
                (SELECT COUNT(*) FROM uploads WHERE status = 'failed') as failed_uploads,
                (SELECT COUNT(*) FROM uploads WHERE DATE(upload_time) = DATE('now')) as today_uploads
        `);

        // Get recent uploads
        const recentUploads = await allAsync(`
            SELECT u.*, f.name as facility_name 
            FROM uploads u
            LEFT JOIN facilities f ON u.facility_id = f.id
            ORDER BY u.upload_time DESC
            LIMIT 10
        `);

        res.render('pages/dashboard', {
            title: 'Dashboard',
            username: req.session.username,
            stats,
            recentUploads
        });
    } catch (error) {
        console.error('Dashboard error:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load dashboard'
        });
    }
});

// Facilities page
router.get('/facilities', requireAdmin, async (req, res) => {
    try {
        const facilities = await allAsync(`
            SELECT f.*,
                   COUNT(u.id) as upload_count,
                   CASE
                       WHEN (
                           SELECT used_at FROM facility_short_codes fsc
                           WHERE fsc.facility_id = f.id
                           ORDER BY fsc.created_at DESC
                           LIMIT 1
                       ) IS NOT NULL THEN 1
                       ELSE 0
                   END as has_active_tablet
            FROM facilities f
            LEFT JOIN uploads u ON f.id = u.facility_id
            GROUP BY f.id
            ORDER BY f.name
        `);

        res.render('pages/facilities', {
            title: 'Facilities',
            username: req.session.username,
            facilities
        });
    } catch (error) {
        console.error('Facilities page error:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load facilities'
        });
    }
});

// Uploads page
router.get('/uploads', requireAdmin, async (req, res) => {
    try {
        const uploads = await allAsync(`
            SELECT u.*, f.name as facility_name
            FROM uploads u
            LEFT JOIN facilities f ON u.facility_id = f.id
            ORDER BY u.upload_time DESC
            LIMIT 100
        `);

        res.render('pages/uploads', {
            title: 'Uploads',
            username: req.session.username,
            uploads
        });
    } catch (error) {
        console.error('Uploads page error:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load uploads'
        });
    }
});

// Get upload JSON data
router.get('/api/uploads/:surveyId', requireAdmin, async (req, res) => {
    try {
        const { surveyId } = req.params;
        const fs = require('fs').promises;

        // Get the file path from the database
        const upload = await getAsync(
            'SELECT file_path FROM uploads WHERE survey_response_id = ?',
            [surveyId]
        );

        if (!upload || !upload.file_path) {
            return res.status(404).json({ error: 'Upload not found' });
        }

        // Read the JSON file
        const jsonData = await fs.readFile(upload.file_path, 'utf-8');
        const data = JSON.parse(jsonData);

        res.json(data);
    } catch (error) {
        console.error('Error fetching upload data:', error);
        res.status(500).json({ error: 'Failed to load upload data' });
    }
});

// Logout
router.get('/logout', (req, res) => {
    req.session.destroy(() => {
        res.redirect('/login');
    });
});

module.exports = router;