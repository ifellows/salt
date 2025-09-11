const { db, getAsync } = require('../../models/database');

// Middleware to check if user is logged in via session
function requireLogin(req, res, next) {
    if (!req.session || !req.session.userId) {
        return res.status(401).json({ 
            status: 'error', 
            message: 'Authentication required' 
        });
    }
    next();
}

// Middleware to check if user is admin via session
function requireAdmin(req, res, next) {
    if (!req.session || !req.session.userId || req.session.role !== 'admin') {
        return res.status(403).json({ 
            status: 'error', 
            message: 'Admin access required' 
        });
    }
    next();
}

// Middleware to check API key for facility tablet access
async function requireFacilityApiKey(req, res, next) {
    const apiKey = req.headers['x-api-key'] || req.headers['authorization']?.replace('Bearer ', '');
    
    if (!apiKey) {
        return res.status(401).json({ 
            status: 'error', 
            message: 'API key required' 
        });
    }
    
    try {
        // Check if API key exists for a facility
        const facility = await getAsync(
            'SELECT id, name, location FROM facilities WHERE api_key = ?',
            [apiKey]
        );
        
        if (!facility) {
            return res.status(401).json({ 
                status: 'error', 
                message: 'Invalid API key' 
            });
        }
        
        // Log API access in audit log
        db.run(
            `INSERT INTO audit_log (user_id, action, entity_type, entity_id, timestamp)
             VALUES (?, ?, ?, ?, datetime('now'))`,
            [`facility_${facility.id}`, 'api_access', 'endpoint', req.path]
        );
        
        // Attach facility info to request for potential use
        req.facility = facility;
        req.authMethod = 'facility_api_key';
        next();
    } catch (error) {
        console.error('Error validating API key:', error);
        return res.status(500).json({ 
            status: 'error', 
            message: 'Internal server error' 
        });
    }
}

// Combined middleware: allow either session login OR facility API key
async function requireAuthOrFacilityApiKey(req, res, next) {
    // Check for API key first
    const apiKey = req.headers['x-api-key'] || req.headers['authorization']?.replace('Bearer ', '');
    
    if (apiKey) {
        try {
            // Try facility API key authentication
            const facility = await getAsync(
                'SELECT id, name, location FROM facilities WHERE api_key = ?',
                [apiKey]
            );
            
            if (facility) {
                // Log API access
                db.run(
                    `INSERT INTO audit_log (user_id, action, entity_type, entity_id, timestamp)
                     VALUES (?, ?, ?, ?, datetime('now'))`,
                    [`facility_${facility.id}`, 'api_access', 'endpoint', req.path]
                );
                
                req.facility = facility;
                req.authMethod = 'facility_api_key';
                return next();
            }
        } catch (error) {
            console.error('Error validating API key:', error);
        }
    }
    
    // Fall back to session authentication
    if (req.session && req.session.userId) {
        req.authMethod = 'session';
        return next();
    }
    
    // Neither authentication method succeeded
    return res.status(401).json({ 
        status: 'error', 
        message: 'Authentication required (session or API key)' 
    });
}

module.exports = {
    requireLogin,
    requireAdmin,
    requireFacilityApiKey,
    requireAuthOrFacilityApiKey
};