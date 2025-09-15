const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { body, validationResult } = require('express-validator');
const { getAsync, runAsync, allAsync } = require('../../models/database');
const { requireAdmin } = require('../middleware/auth');
const { logAudit } = require('../../services/auditService');
const router = express.Router();

// All routes require admin authentication
router.use(requireAdmin);

// List all facilities
router.get('/', async (req, res) => {
    try {
        const facilities = await allAsync(
            `SELECT id, name, location, 
                    allow_non_coupon_participants, 
                    coupons_to_issue,
                    created_at, updated_at 
             FROM facilities ORDER BY name`
        );
        res.json(facilities);
    } catch (error) {
        console.error('Error fetching facilities:', error);
        res.status(500).json({ error: 'Failed to fetch facilities' });
    }
});

// Get single facility
router.get('/:id', async (req, res) => {
    try {
        const facility = await getAsync(
            `SELECT id, name, location, api_key, 
                    allow_non_coupon_participants, 
                    coupons_to_issue,
                    created_at, updated_at 
             FROM facilities WHERE id = ?`,
            [req.params.id]
        );
        
        if (!facility) {
            return res.status(404).json({ error: 'Facility not found' });
        }
        
        // Get upload statistics
        const stats = await getAsync(
            `SELECT 
                COUNT(*) as total_uploads,
                COUNT(CASE WHEN status = 'completed' THEN 1 END) as completed_uploads,
                COUNT(CASE WHEN status = 'failed' THEN 1 END) as failed_uploads
             FROM uploads WHERE facility_id = ?`,
            [req.params.id]
        );
        
        res.json({ ...facility, statistics: stats });
    } catch (error) {
        console.error('Error fetching facility:', error);
        res.status(500).json({ error: 'Failed to fetch facility' });
    }
});

// Create new facility
router.post('/', [
    body('name').notEmpty().trim(),
    body('location').optional().trim(),
    body('allow_non_coupon_participants').optional().isBoolean(),
    body('coupons_to_issue').optional().isInt({ min: 0, max: 10 })
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { name, location, allow_non_coupon_participants = true, coupons_to_issue = 3 } = req.body;
    const apiKey = `salt_${uuidv4()}`;

    try {
        const result = await runAsync(
            `INSERT INTO facilities (name, location, api_key, allow_non_coupon_participants, coupons_to_issue) 
             VALUES (?, ?, ?, ?, ?)`,
            [name, location, apiKey, allow_non_coupon_participants ? 1 : 0, coupons_to_issue]
        );

        await logAudit(
            req.session.userId,
            'CREATE_FACILITY',
            'facility',
            result.id,
            null,
            { name, location }
        );

        res.status(201).json({
            id: result.id,
            name,
            location,
            api_key: apiKey,
            message: 'Facility created successfully'
        });
    } catch (error) {
        console.error('Error creating facility:', error);
        res.status(500).json({ error: 'Failed to create facility' });
    }
});

// Update facility
router.put('/:id', [
    body('name').optional().trim(),
    body('location').optional().trim(),
    body('allow_non_coupon_participants').optional().isBoolean(),
    body('coupons_to_issue').optional().isInt({ min: 0, max: 10 })
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { name, location, allow_non_coupon_participants, coupons_to_issue } = req.body;
    const facilityId = req.params.id;
    
    console.log('Facility update request:', {
        facilityId,
        name,
        location,
        allow_non_coupon_participants,
        coupons_to_issue,
        body: req.body
    });

    try {
        // Get current facility for audit
        const oldFacility = await getAsync(
            'SELECT name, location, allow_non_coupon_participants, coupons_to_issue FROM facilities WHERE id = ?',
            [facilityId]
        );

        if (!oldFacility) {
            return res.status(404).json({ error: 'Facility not found' });
        }

        // Build update query dynamically based on provided fields
        const updates = [];
        const values = [];
        
        if (name !== undefined) {
            updates.push('name = ?');
            values.push(name);
        }
        if (location !== undefined) {
            updates.push('location = ?');
            values.push(location);
        }
        if (allow_non_coupon_participants !== undefined) {
            updates.push('allow_non_coupon_participants = ?');
            values.push(allow_non_coupon_participants ? 1 : 0);
        }
        if (coupons_to_issue !== undefined) {
            updates.push('coupons_to_issue = ?');
            values.push(coupons_to_issue);
        }
        
        if (updates.length > 0) {
            updates.push('updated_at = CURRENT_TIMESTAMP');
            values.push(facilityId);
            
            // Update facility
            await runAsync(
                `UPDATE facilities SET ${updates.join(', ')} WHERE id = ?`,
                values
            );
        }

        await logAudit(
            req.session.userId,
            'UPDATE_FACILITY',
            'facility',
            facilityId,
            oldFacility,
            { name: name || oldFacility.name, location: location || oldFacility.location }
        );

        res.json({ message: 'Facility updated successfully' });
    } catch (error) {
        console.error('Error updating facility:', error);
        res.status(500).json({ error: 'Failed to update facility' });
    }
});

// Regenerate API key
router.post('/:id/regenerate-key', async (req, res) => {
    const facilityId = req.params.id;
    const newApiKey = `salt_${uuidv4()}`;

    try {
        const facility = await getAsync(
            'SELECT api_key FROM facilities WHERE id = ?',
            [facilityId]
        );

        if (!facility) {
            return res.status(404).json({ error: 'Facility not found' });
        }

        await runAsync(
            'UPDATE facilities SET api_key = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?',
            [newApiKey, facilityId]
        );

        await logAudit(
            req.session.userId,
            'REGENERATE_API_KEY',
            'facility',
            facilityId,
            { api_key: facility.api_key },
            { api_key: newApiKey }
        );

        res.json({ 
            api_key: newApiKey,
            message: 'API key regenerated successfully' 
        });
    } catch (error) {
        console.error('Error regenerating API key:', error);
        res.status(500).json({ error: 'Failed to regenerate API key' });
    }
});

// Delete facility
router.delete('/:id', async (req, res) => {
    const facilityId = req.params.id;

    try {
        // Check if facility has uploads
        const uploadCount = await getAsync(
            'SELECT COUNT(*) as count FROM uploads WHERE facility_id = ?',
            [facilityId]
        );

        if (uploadCount.count > 0) {
            return res.status(400).json({ 
                error: 'Cannot delete facility with existing uploads' 
            });
        }

        const facility = await getAsync(
            'SELECT * FROM facilities WHERE id = ?',
            [facilityId]
        );

        if (!facility) {
            return res.status(404).json({ error: 'Facility not found' });
        }

        await runAsync('DELETE FROM facilities WHERE id = ?', [facilityId]);

        await logAudit(
            req.session.userId,
            'DELETE_FACILITY',
            'facility',
            facilityId,
            facility,
            null
        );

        res.json({ message: 'Facility deleted successfully' });
    } catch (error) {
        console.error('Error deleting facility:', error);
        res.status(500).json({ error: 'Failed to delete facility' });
    }
});

module.exports = router;