const express = require('express');
const { db, getAsync } = require('../../models/database');
const { requireFacilityApiKey } = require('../../web/middleware/auth');
const router = express.Router();

/**
 * Get facility configuration
 * GET /api/sync/facility/config
 */
router.get('/facility/config', requireFacilityApiKey, async (req, res) => {
    try {
        // Get facility from auth middleware
        const facilityId = req.facility.id;
        
        // Get facility configuration
        const facility = await getAsync(
            `SELECT 
                id as facility_id,
                name as facility_name,
                allow_non_coupon_participants,
                coupons_to_issue,
                seed_recruitment_active,
                seed_contact_rate_days,
                seed_recruitment_window_min_days,
                seed_recruitment_window_max_days
             FROM facilities 
             WHERE id = ?`,
            [facilityId]
        );
        
        if (!facility) {
            return res.status(404).json({
                status: 'error',
                message: 'Facility configuration not found'
            });
        }
        
        // Return facility configuration
        res.json({
            status: 'success',
            data: {
                facility_id: facility.facility_id,
                facility_name: facility.facility_name,
                allow_non_coupon_participants: Boolean(facility.allow_non_coupon_participants),
                coupons_to_issue: facility.coupons_to_issue || 3,
                seed_recruitment_active: Boolean(facility.seed_recruitment_active),
                seed_contact_rate_days: facility.seed_contact_rate_days || 7,
                seed_recruitment_window_min_days: facility.seed_recruitment_window_min_days || 0,
                seed_recruitment_window_max_days: facility.seed_recruitment_window_max_days || 730,
                sync_time: new Date().toISOString()
            }
        });
        
        console.log(`Facility config sent to facility ${facility.facility_name}: allow_non_coupon=${facility.allow_non_coupon_participants}, coupons=${facility.coupons_to_issue}`);
        
    } catch (error) {
        console.error('Error fetching facility config:', error);
        res.status(500).json({
            status: 'error',
            message: 'Failed to fetch facility configuration',
            error: process.env.NODE_ENV === 'development' ? error.message : undefined
        });
    }
});

module.exports = router;