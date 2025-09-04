const { getAsync } = require('../../models/database');

// Middleware to validate API key for tablet requests
async function validateApiKey(req, res, next) {
    const apiKey = req.headers['x-api-key'] || req.headers['authorization']?.replace('Bearer ', '');

    if (!apiKey) {
        return res.status(401).json({ error: 'API key required' });
    }

    try {
        // Get facility by API key
        const facility = await getAsync(
            'SELECT * FROM facilities WHERE api_key = ?',
            [apiKey]
        );

        if (!facility) {
            return res.status(401).json({ error: 'Invalid API key' });
        }

        // Attach facility to request
        req.facility = facility;
        next();

    } catch (error) {
        console.error('API key validation error:', error);
        res.status(500).json({ error: 'Authentication failed' });
    }
}

// Middleware to check if admin is logged in (for web routes)
function requireAdmin(req, res, next) {
    if (!req.session.userId) {
        if (req.path.startsWith('/api')) {
            return res.status(401).json({ error: 'Authentication required' });
        } else {
            return res.redirect('/login');
        }
    }
    next();
}

module.exports = {
    validateApiKey,
    requireAdmin
};