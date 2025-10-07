const express = require('express');
const router = express.Router();
const { allAsync, getAsync } = require('../../models/database');
const { requireAdmin } = require('../../middleware/auth');

/**
 * Export survey data in long format (one row per variable)
 * GET /api/admin/export/long
 */
router.get('/export/long', requireAdmin, async (req, res) => {
    try {
        const { start_date, end_date, facility_id, preview } = req.query;

        // Build WHERE clause for filtering
        let whereConditions = ['1=1'];
        let params = [];

        if (start_date) {
            whereConditions.push('cs.completed_at >= ?');
            params.push(start_date);
        }

        if (end_date) {
            whereConditions.push('cs.completed_at <= ?');
            params.push(end_date);
        }

        if (facility_id) {
            whereConditions.push('cs.facility_id = ?');
            params.push(facility_id);
        }

        const whereClause = whereConditions.join(' AND ');
        const limitClause = preview === 'true' ? 'LIMIT 500' : '';

        // Query to get all data in long format
        const query = `
            -- Metadata fields
            SELECT
                cs.survey_response_id as survey_id,
                cs.participant_id,
                'meta_survey_id' as variable,
                NULL as numeric_value,
                cs.survey_response_id as text_value
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_participant_id' as variable,
                NULL,
                cs.participant_id
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_facility_id' as variable,
                cs.facility_id,
                NULL
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_facility_name' as variable,
                NULL,
                f.name
            FROM completed_surveys cs
            JOIN facilities f ON cs.facility_id = f.id
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_started_at' as variable,
                NULL,
                cs.started_at
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_completed_at' as variable,
                NULL,
                cs.completed_at
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_language' as variable,
                NULL,
                cs.language
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            -- Device information
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'device_id' as variable,
                NULL,
                cs.device_id
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.device_id IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'device_model' as variable,
                NULL,
                cs.device_model
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.device_model IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'device_android_version' as variable,
                NULL,
                cs.android_version
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.android_version IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'device_app_version' as variable,
                NULL,
                cs.app_version
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.app_version IS NOT NULL

            UNION ALL

            -- Coupon information
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'coupon_referral_used' as variable,
                NULL,
                cs.referral_coupon_code
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.referral_coupon_code IS NOT NULL

            UNION ALL

            -- Survey question responses
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'q_' || sr.question_short_name as variable,
                CASE
                    WHEN sr.answer_type = 'multiple_choice' THEN sr.response_option_index
                    WHEN sr.answer_type = 'numeric' THEN CAST(sr.response_value AS REAL)
                    ELSE NULL
                END as numeric_value,
                CASE
                    WHEN sr.answer_type = 'multiple_choice' THEN sr.response_option_text
                    WHEN sr.answer_type = 'multi_select' THEN sr.response_multi_indices
                    ELSE sr.response_value
                END as text_value
            FROM completed_surveys cs
            JOIN survey_responses sr ON cs.id = sr.completed_survey_id
            WHERE ${whereClause} AND sr.question_short_name IS NOT NULL

            UNION ALL

            -- Rapid test results
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'rapid_' || rt.test_id || '_result' as variable,
                NULL,
                rt.result
            FROM completed_surveys cs
            JOIN rapid_test_results rt ON cs.id = rt.completed_survey_id
            WHERE ${whereClause}

            UNION ALL

            -- Lab results
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'lab_' || REPLACE(LOWER(ltc.test_name), ' ', '_') as variable,
                lr.result_numeric,
                lr.result_value
            FROM completed_surveys cs
            JOIN lab_results lr ON cs.participant_id = lr.subject_id
            JOIN lab_test_configurations ltc ON lr.test_id = ltc.id
            WHERE ${whereClause}

            UNION ALL

            -- Payment information
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'pay_confirmed' as variable,
                CASE WHEN sp.payment_confirmed = 1 THEN 1 ELSE 0 END,
                NULL
            FROM completed_surveys cs
            LEFT JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'pay_amount' as variable,
                sp.payment_amount,
                NULL
            FROM completed_surveys cs
            JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause} AND sp.payment_amount IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'pay_type' as variable,
                NULL,
                sp.payment_type
            FROM completed_surveys cs
            JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause} AND sp.payment_type IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'pay_date' as variable,
                NULL,
                sp.payment_date
            FROM completed_surveys cs
            JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause} AND sp.payment_date IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'sample_collected' as variable,
                CASE WHEN sp.sample_collected = 1 THEN 1 ELSE 0 END,
                NULL
            FROM completed_surveys cs
            LEFT JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause}

            ORDER BY survey_id, variable
            ${limitClause}
        `;

        // Execute query with repeated params for each UNION section
        const fullParams = [];
        const unionSections = query.split('UNION ALL').length;
        for (let i = 0; i < unionSections; i++) {
            fullParams.push(...params);
        }

        const rows = await allAsync(query, fullParams);

        // Process issued coupons (from JSON)
        const surveysWithCoupons = await allAsync(`
            SELECT survey_response_id, participant_id, issued_coupons
            FROM completed_surveys
            WHERE ${whereClause} AND issued_coupons IS NOT NULL
        `, params);

        for (const survey of surveysWithCoupons) {
            try {
                const coupons = JSON.parse(survey.issued_coupons || '[]');
                if (Array.isArray(coupons)) {
                    // Add coupon count
                    rows.push({
                        survey_id: survey.survey_response_id,
                        participant_id: survey.participant_id,
                        variable: 'coupon_issued_count',
                        numeric_value: coupons.length,
                        text_value: null
                    });

                    // Add individual coupons
                    coupons.forEach((coupon, index) => {
                        rows.push({
                            survey_id: survey.survey_response_id,
                            participant_id: survey.participant_id,
                            variable: `coupon_issued_${index + 1}`,
                            numeric_value: null,
                            text_value: coupon
                        });
                    });
                }
            } catch (e) {
                console.error('Error parsing coupons for survey', survey.survey_response_id, e);
            }
        }

        // Set response headers for CSV download
        if (preview !== 'true') {
            const date = new Date().toISOString().split('T')[0];
            res.setHeader('Content-Type', 'text/csv; charset=utf-8');
            res.setHeader('Content-Disposition', `attachment; filename="salt_export_long_${date}.csv"`);

            // Add UTF-8 BOM for Excel compatibility
            res.write('\ufeff');
        } else {
            res.setHeader('Content-Type', 'application/json');
        }

        if (preview === 'true') {
            // Return JSON for preview
            res.json({
                count: rows.length,
                preview: rows.slice(0, 100),
                columns: ['survey_id', 'participant_id', 'variable', 'numeric_value', 'text_value']
            });
        } else {
            // Write CSV headers
            res.write('survey_id,participant_id,variable,numeric_value,text_value\n');

            // Stream CSV rows
            for (const row of rows) {
                const csvRow = [
                    row.survey_id || '',
                    row.participant_id || '',
                    row.variable || '',
                    row.numeric_value !== null && row.numeric_value !== undefined ? row.numeric_value : '',
                    row.text_value || ''
                ].map(field => {
                    // Escape fields containing commas, quotes, or newlines
                    const str = String(field);
                    if (str.includes(',') || str.includes('"') || str.includes('\n')) {
                        return `"${str.replace(/"/g, '""')}"`;
                    }
                    return str;
                }).join(',');

                res.write(csvRow + '\n');
            }

            res.end();
        }

    } catch (error) {
        console.error('Error exporting data in long format:', error);
        res.status(500).json({ error: 'Failed to export data' });
    }
});

/**
 * Export survey data in wide format (one row per survey)
 * GET /api/admin/export/wide
 */
router.get('/export/wide', requireAdmin, async (req, res) => {
    try {
        const { start_date, end_date, facility_id, preview, valueType } = req.query;

        // Build WHERE clause for filtering
        let whereConditions = ['1=1'];
        let params = [];

        if (start_date) {
            whereConditions.push('cs.completed_at >= ?');
            params.push(start_date);
        }

        if (end_date) {
            whereConditions.push('cs.completed_at <= ?');
            params.push(end_date);
        }

        if (facility_id) {
            whereConditions.push('cs.facility_id = ?');
            params.push(facility_id);
        }

        const whereClause = whereConditions.join(' AND ');

        // First, get the long format data
        const query = `
            -- Metadata fields
            SELECT
                cs.survey_response_id as survey_id,
                cs.participant_id,
                'meta_survey_id' as variable,
                NULL as numeric_value,
                cs.survey_response_id as text_value
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_participant_id' as variable,
                NULL,
                cs.participant_id
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_facility_id' as variable,
                cs.facility_id,
                NULL
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_facility_name' as variable,
                NULL,
                f.name
            FROM completed_surveys cs
            JOIN facilities f ON cs.facility_id = f.id
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_started_at' as variable,
                NULL,
                cs.started_at
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_completed_at' as variable,
                NULL,
                cs.completed_at
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'meta_language' as variable,
                NULL,
                cs.language
            FROM completed_surveys cs
            WHERE ${whereClause}

            UNION ALL

            -- Device information
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'device_id' as variable,
                NULL,
                cs.device_id
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.device_id IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'device_model' as variable,
                NULL,
                cs.device_model
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.device_model IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'device_android_version' as variable,
                NULL,
                cs.android_version
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.android_version IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'device_app_version' as variable,
                NULL,
                cs.app_version
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.app_version IS NOT NULL

            UNION ALL

            -- Coupon information
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'coupon_referral_used' as variable,
                NULL,
                cs.referral_coupon_code
            FROM completed_surveys cs
            WHERE ${whereClause} AND cs.referral_coupon_code IS NOT NULL

            UNION ALL

            -- Survey question responses
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'q_' || sr.question_short_name as variable,
                CASE
                    WHEN sr.answer_type = 'multiple_choice' THEN sr.response_option_index
                    WHEN sr.answer_type = 'numeric' THEN CAST(sr.response_value AS REAL)
                    ELSE NULL
                END as numeric_value,
                CASE
                    WHEN sr.answer_type = 'multiple_choice' THEN sr.response_option_text
                    WHEN sr.answer_type = 'multi_select' THEN sr.response_multi_indices
                    ELSE sr.response_value
                END as text_value
            FROM completed_surveys cs
            JOIN survey_responses sr ON cs.id = sr.completed_survey_id
            WHERE ${whereClause} AND sr.question_short_name IS NOT NULL

            UNION ALL

            -- Rapid test results
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'rapid_' || rt.test_id || '_result' as variable,
                NULL,
                rt.result
            FROM completed_surveys cs
            JOIN rapid_test_results rt ON cs.id = rt.completed_survey_id
            WHERE ${whereClause}

            UNION ALL

            -- Lab results
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'lab_' || REPLACE(LOWER(ltc.test_name), ' ', '_') as variable,
                lr.result_numeric,
                lr.result_value
            FROM completed_surveys cs
            JOIN lab_results lr ON cs.participant_id = lr.subject_id
            JOIN lab_test_configurations ltc ON lr.test_id = ltc.id
            WHERE ${whereClause}

            UNION ALL

            -- Payment information
            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'pay_confirmed' as variable,
                CASE WHEN sp.payment_confirmed = 1 THEN 1 ELSE 0 END,
                NULL
            FROM completed_surveys cs
            LEFT JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause}

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'pay_amount' as variable,
                sp.payment_amount,
                NULL
            FROM completed_surveys cs
            JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause} AND sp.payment_amount IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'pay_type' as variable,
                NULL,
                sp.payment_type
            FROM completed_surveys cs
            JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause} AND sp.payment_type IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'pay_date' as variable,
                NULL,
                sp.payment_date
            FROM completed_surveys cs
            JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause} AND sp.payment_date IS NOT NULL

            UNION ALL

            SELECT
                cs.survey_response_id,
                cs.participant_id,
                'sample_collected' as variable,
                CASE WHEN sp.sample_collected = 1 THEN 1 ELSE 0 END,
                NULL
            FROM completed_surveys cs
            LEFT JOIN survey_payments sp ON cs.id = sp.completed_survey_id
            WHERE ${whereClause}

            ORDER BY survey_id, variable
        `;

        // Execute query with repeated params for each UNION section
        const fullParams = [];
        const unionSections = query.split('UNION ALL').length;
        for (let i = 0; i < unionSections; i++) {
            fullParams.push(...params);
        }

        const longRows = await allAsync(query, fullParams);

        // Process issued coupons (from JSON)
        const surveysWithCoupons = await allAsync(`
            SELECT survey_response_id, participant_id, issued_coupons
            FROM completed_surveys
            WHERE ${whereClause} AND issued_coupons IS NOT NULL
        `, params);

        for (const survey of surveysWithCoupons) {
            try {
                const coupons = JSON.parse(survey.issued_coupons || '[]');
                if (Array.isArray(coupons)) {
                    // Add coupon count
                    longRows.push({
                        survey_id: survey.survey_response_id,
                        participant_id: survey.participant_id,
                        variable: 'coupon_issued_count',
                        numeric_value: coupons.length,
                        text_value: null
                    });

                    // Add individual coupons
                    coupons.forEach((coupon, index) => {
                        longRows.push({
                            survey_id: survey.survey_response_id,
                            participant_id: survey.participant_id,
                            variable: `coupon_issued_${index + 1}`,
                            numeric_value: null,
                            text_value: coupon
                        });
                    });
                }
            } catch (e) {
                console.error('Error parsing coupons for survey', survey.survey_response_id, e);
            }
        }

        // Pivot to wide format
        const wideData = {};
        const allColumns = new Set(['survey_id', 'participant_id']);

        // Group by survey_id
        for (const row of longRows) {
            if (!wideData[row.survey_id]) {
                wideData[row.survey_id] = {
                    survey_id: row.survey_id,
                    participant_id: row.participant_id
                };
            }

            // Add the variable as a column
            // Determine which value to use based on valueType parameter
            let value;
            if (valueType === 'text') {
                // Use text when available, otherwise use numeric
                value = row.text_value !== null && row.text_value !== undefined && row.text_value !== ''
                    ? row.text_value
                    : row.numeric_value;
            } else {
                // Default behavior: use numeric when available, otherwise use text
                value = row.numeric_value !== null && row.numeric_value !== undefined
                    ? row.numeric_value
                    : row.text_value;
            }

            wideData[row.survey_id][row.variable] = value;
            allColumns.add(row.variable);
        }

        // Convert to array and sort columns
        const wideRows = Object.values(wideData);
        const sortedColumns = Array.from(allColumns).sort((a, b) => {
            // Sort order: survey_id, participant_id, meta_, device_, q_, rapid_, lab_, pay_, sample_, coupon_
            const order = ['survey_id', 'participant_id'];
            const prefixes = ['meta_', 'device_', 'q_', 'rapid_', 'lab_', 'pay_', 'sample_', 'coupon_'];

            if (order.includes(a)) return order.includes(b) ? order.indexOf(a) - order.indexOf(b) : -1;
            if (order.includes(b)) return 1;

            const prefixA = prefixes.find(p => a.startsWith(p));
            const prefixB = prefixes.find(p => b.startsWith(p));

            if (prefixA && prefixB) {
                if (prefixA === prefixB) return a.localeCompare(b);
                return prefixes.indexOf(prefixA) - prefixes.indexOf(prefixB);
            }
            if (prefixA) return -1;
            if (prefixB) return 1;

            return a.localeCompare(b);
        });

        // Set response headers
        if (preview !== 'true') {
            const date = new Date().toISOString().split('T')[0];
            res.setHeader('Content-Type', 'text/csv; charset=utf-8');
            res.setHeader('Content-Disposition', `attachment; filename="salt_export_wide_${date}.csv"`);

            // Add UTF-8 BOM for Excel compatibility
            res.write('\ufeff');
        } else {
            res.setHeader('Content-Type', 'application/json');
        }

        if (preview === 'true') {
            // Return JSON for preview
            res.json({
                count: wideRows.length,
                preview: wideRows.slice(0, 10),
                columns: sortedColumns
            });
        } else {
            // Write CSV headers
            res.write(sortedColumns.map(col => {
                // Escape column names if needed
                if (col.includes(',') || col.includes('"') || col.includes('\n')) {
                    return `"${col.replace(/"/g, '""')}"`;
                }
                return col;
            }).join(',') + '\n');

            // Stream CSV rows
            for (const row of wideRows) {
                const csvRow = sortedColumns.map(col => {
                    const value = row[col];
                    if (value === null || value === undefined) return '';

                    const str = String(value);
                    // Escape fields containing commas, quotes, or newlines
                    if (str.includes(',') || str.includes('"') || str.includes('\n')) {
                        return `"${str.replace(/"/g, '""')}"`;
                    }
                    return str;
                }).join(',');

                res.write(csvRow + '\n');
            }

            res.end();
        }

    } catch (error) {
        console.error('Error exporting data in wide format:', error);
        res.status(500).json({ error: 'Failed to export data' });
    }
});

module.exports = router;