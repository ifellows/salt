const express = require('express');
const router = express.Router();

/**
 * Health check endpoint for SALT system validation
 * GET /api/health
 *
 * Returns system health status and identifies this as a SALT server.
 * Used by Android app during initial setup to verify server compatibility.
 * No authentication required.
 */
router.get('/', (req, res) => {
    res.json({
        status: 'ok',
        version: '1.0.0',
        system: 'SALT',
        timestamp: new Date().toISOString()
    });
});

module.exports = router;
