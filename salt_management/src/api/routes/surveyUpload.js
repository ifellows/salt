const express = require('express');
const path = require('path');
const fs = require('fs').promises;
const { db, runAsync, getAsync } = require('../../models/database');
const { requireFacilityApiKey } = require('../../web/middleware/auth');
const router = express.Router();

/**
 * Upload a completed survey from facility tablet
 * POST /api/sync/survey/upload
 */
router.post('/survey/upload', requireFacilityApiKey, async (req, res) => {
    try {
        // Get facility info from auth middleware
        const facility = req.facility;
        
        // Extract survey data from request body
        // The Android app sends data in a flat structure
        const surveyData = req.body;
        
        // Debug logging
        console.log('Received upload data keys:', Object.keys(surveyData));
        console.log('Survey ID field:', surveyData.surveyId);
        
        // Validate required fields
        if (!surveyData.surveyId) {
            console.error('Missing survey ID. Received data:', JSON.stringify(surveyData).substring(0, 200));
            return res.status(400).json({
                status: 'error',
                message: 'Invalid survey data: missing survey ID'
            });
        }
        
        if (!surveyData.answers || !Array.isArray(surveyData.answers)) {
            return res.status(400).json({
                status: 'error',
                message: 'Invalid survey data: missing or invalid answers array'
            });
        }
        
        // Check if this survey has already been uploaded (prevent duplicates)
        const existingUpload = await getAsync(
            'SELECT id FROM uploads WHERE survey_response_id = ?',
            [surveyData.surveyId]
        );
        
        if (existingUpload) {
            // Survey already uploaded, return success to allow client to clear it
            console.log(`Survey ${surveyData.surveyId} already uploaded, returning success`);
            return res.json({
                status: 'success',
                message: 'Survey already uploaded',
                data: {
                    survey_id: surveyData.surveyId,
                    upload_id: existingUpload.id,
                    duplicate: true
                }
            });
        }
        
        // Generate filename for JSON storage
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `survey_${facility.id}_${timestamp}_${surveyData.surveyId}.json`;
        
        // Prepare upload data
        const uploadData = {
            survey_response_id: surveyData.surveyId,
            facility_id: facility.id,
            facility_name: facility.name,
            upload_time: new Date().toISOString(),
            survey_data: surveyData
        };
        
        // Save to file system
        const uploadsDir = path.join(__dirname, '..', '..', '..', 'data', 'uploads');
        await fs.mkdir(uploadsDir, { recursive: true });
        const filepath = path.join(uploadsDir, filename);
        await fs.writeFile(filepath, JSON.stringify(uploadData, null, 2));
        
        // Save upload record to database
        const result = await runAsync(
            `INSERT INTO uploads (
                survey_response_id,
                facility_id,
                upload_time,
                file_path,
                status,
                participant_id
            ) VALUES (?, ?, datetime('now'), ?, ?, ?)`,
            [
                surveyData.surveyId,
                facility.id,
                filepath,
                'COMPLETED',
                surveyData.subjectId || null
            ]
        );
        
        // Log the upload in audit log
        await runAsync(
            `INSERT INTO audit_log (user_id, action, entity_type, entity_id, new_value, timestamp)
             VALUES (?, ?, ?, ?, ?, datetime('now'))`,
            [
                `facility_${facility.id}`,
                'survey_upload',
                'survey',
                surveyData.surveyId,
                JSON.stringify({ 
                    facility: facility.name,
                    answers_count: surveyData.answers.length,
                    device: surveyData.deviceInfo?.deviceModel 
                })
            ]
        );
        
        // Log coupon information if present
        if (surveyData.referralCouponCode) {
            console.log(`Survey ${surveyData.surveyId} used referral coupon: ${surveyData.referralCouponCode}`);
        }
        if (surveyData.issuedCoupons && surveyData.issuedCoupons.length > 0) {
            console.log(`Survey ${surveyData.surveyId} issued ${surveyData.issuedCoupons.length} coupons: ${surveyData.issuedCoupons.join(', ')}`);
        }
        
        console.log(`Survey ${surveyData.surveyId} uploaded successfully from facility ${facility.name}`);
        
        // Return success response
        res.json({
            status: 'success',
            message: 'Survey uploaded successfully',
            data: {
                survey_id: surveyData.surveyId,
                upload_id: result.id,
                uploaded_at: new Date().toISOString()
            }
        });
        
    } catch (error) {
        console.error('Error uploading survey:', error);
        
        // Log error in audit log
        if (req.facility) {
            await runAsync(
                `INSERT INTO audit_log (user_id, action, entity_type, entity_id, old_value, timestamp)
                 VALUES (?, ?, ?, ?, ?, datetime('now'))`,
                [
                    `facility_${req.facility.id}`,
                    'survey_upload_error',
                    'error',
                    req.body?.survey?.id || 'unknown',
                    error.message
                ]
            ).catch(console.error);
        }
        
        res.status(500).json({
            status: 'error',
            message: 'Failed to upload survey',
            error: process.env.NODE_ENV === 'development' ? error.message : undefined
        });
    }
});

/**
 * Get upload statistics for a facility
 * GET /api/sync/survey/upload/stats
 */
router.get('/survey/upload/stats', requireFacilityApiKey, async (req, res) => {
    try {
        const facility = req.facility;
        
        // Get upload statistics
        const stats = await getAsync(
            `SELECT 
                COUNT(*) as total_uploads,
                COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed,
                COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed,
                MIN(upload_time) as first_upload,
                MAX(upload_time) as last_upload
             FROM uploads
             WHERE facility_id = ?`,
            [facility.id]
        );
        
        res.json({
            status: 'success',
            data: {
                facility_id: facility.id,
                facility_name: facility.name,
                statistics: stats
            }
        });
        
    } catch (error) {
        console.error('Error fetching upload stats:', error);
        res.status(500).json({
            status: 'error',
            message: 'Failed to fetch upload statistics'
        });
    }
});

module.exports = router;