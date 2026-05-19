/**
 * Facility Restore Route
 *
 * After a tablet is wiped and reinstalled, the local Room DB has no record of
 * coupons it previously issued, which were enrolled, or which had recruitment
 * payments made. This endpoint returns enough state to reconstitute that
 * picture so recruitment chains aren't broken.
 *
 * Authoritative source: completed_surveys. Each row's `issued_coupons` JSON
 * array tells us which coupons that survey handed out, and `referral_coupon_code`
 * tells us which coupon the participant used to enroll. We derive the entire
 * coupon graph from these fields rather than trusting coupon_usage, which has
 * historically been clobbered by the survey upload path (an older
 * INSERT OR REPLACE on the used-side wiped the issued-side of the row).
 * coupon_usage is still consulted as a fallback for any code we don't see in
 * completed_surveys.
 *
 * Stub records also include `paymentConfirmed` so the recruitment payment
 * screen can correctly classify already-restored chain participants as
 * eligible-but-unpaid (PENDING) instead of falling into the
 * "completed-but-not-paid = INELIGIBLE" branch.
 *
 * Recruitment payment dates come from JSON files on disk (no DB table for
 * those).
 *
 * GET /api/sync/facility/restore
 */
const express = require('express');
const path = require('path');
const fs = require('fs').promises;
const { allAsync } = require('../../models/database');
const { requireFacilityApiKey } = require('../../web/middleware/auth');
const router = express.Router();

router.get('/facility/restore', requireFacilityApiKey, async (req, res) => {
    try {
        const facilityId = req.facility.id;
        const facilityName = req.facility.name;

        const stubRows = await allAsync(
            `SELECT cs.survey_response_id, cs.participant_id, cs.started_at,
                    cs.completed_at, cs.language, cs.referral_coupon_code,
                    cs.issued_coupons, cs.recruitment_depth,
                    COALESCE(sp.payment_confirmed, 0) AS payment_confirmed
             FROM completed_surveys cs
             LEFT JOIN survey_payments sp ON sp.completed_survey_id = cs.id
             WHERE cs.facility_id = ?
             ORDER BY cs.completed_at`,
            [facilityId]
        );

        // Build the coupon graph from completed_surveys (authoritative).
        const couponState = Object.create(null);
        for (const r of stubRows) {
            if (r.issued_coupons) {
                let codes;
                try {
                    codes = JSON.parse(r.issued_coupons);
                } catch (e) {
                    console.warn(`facility/restore: bad issued_coupons JSON for survey ${r.survey_response_id}: ${e.message}`);
                    codes = null;
                }
                if (Array.isArray(codes)) {
                    for (const code of codes) {
                        if (!code) continue;
                        const entry = couponState[code] || (couponState[code] = { code });
                        entry.issuedBySurveyId = r.survey_response_id;
                        entry.issuedAt = r.completed_at;
                    }
                }
            }
            if (r.referral_coupon_code) {
                const code = r.referral_coupon_code;
                const entry = couponState[code] || (couponState[code] = { code });
                entry.usedBySurveyId = r.survey_response_id;
                entry.usedAt = r.completed_at;
            }
        }

        // Fall back to coupon_usage for any coupons not seen above (defensive
        // — e.g. orphaned rows). Don't let coupon_usage overwrite the derived
        // values; only fill in gaps.
        const usageRows = await allAsync(
            `SELECT coupon_code, issued_by_survey_id, used_by_survey_id,
                    issued_at, used_at
             FROM coupon_usage
             WHERE facility_id = ?`,
            [facilityId]
        );
        for (const r of usageRows) {
            const entry = couponState[r.coupon_code] || (couponState[r.coupon_code] = { code: r.coupon_code });
            if (!entry.issuedBySurveyId && r.issued_by_survey_id) {
                entry.issuedBySurveyId = r.issued_by_survey_id;
                entry.issuedAt = entry.issuedAt || r.issued_at;
            }
            if (!entry.usedBySurveyId && r.used_by_survey_id) {
                entry.usedBySurveyId = r.used_by_survey_id;
                entry.usedAt = entry.usedAt || r.used_at;
            }
        }

        // Recruitment payment dates from JSON files on disk.
        const uploadsDir = path.join(__dirname, '..', '..', '..', 'data',
                                     'uploads', 'recruitment_payments');
        const paymentDateByCode = Object.create(null);
        try {
            const files = await fs.readdir(uploadsDir);
            const prefix = `payment_${facilityId}_`;
            for (const f of files) {
                if (!f.startsWith(prefix)) continue;
                try {
                    const raw = await fs.readFile(path.join(uploadsDir, f), 'utf8');
                    const parsed = JSON.parse(raw);
                    if (parsed.facility_id !== facilityId) continue;
                    const codes = parsed.payment_data && parsed.payment_data.couponCodes;
                    const paidAt = parsed.payment_data && parsed.payment_data.paymentDate;
                    if (Array.isArray(codes) && paidAt) {
                        for (const code of codes) {
                            const prior = paymentDateByCode[code];
                            if (!prior || new Date(paidAt) < new Date(prior)) {
                                paymentDateByCode[code] = paidAt;
                            }
                        }
                    }
                } catch (innerErr) {
                    console.warn(`facility/restore: skipping unreadable payment file ${f}: ${innerErr.message}`);
                }
            }
        } catch (err) {
            // No payments dir yet => no recruitment payments to attach.
        }

        const coupons = Object.values(couponState).map(c => ({
            code: c.code,
            issuedBySurveyId: c.issuedBySurveyId || null,
            usedBySurveyId: c.usedBySurveyId || null,
            issuedAt: c.issuedAt || null,
            usedAt: c.usedAt || null,
            status: c.usedAt ? 'USED' : c.issuedAt ? 'ISSUED' : 'UNUSED',
            recruitmentPaymentDate: paymentDateByCode[c.code] || null
        }));

        const surveyStubs = stubRows.map(r => ({
            id: r.survey_response_id,
            participantId: r.participant_id,
            startedAt: r.started_at,
            completedAt: r.completed_at,
            language: r.language,
            referralCouponCode: r.referral_coupon_code,
            issuedCoupons: r.issued_coupons,
            recruitmentDepth: r.recruitment_depth,
            paymentConfirmed: Boolean(r.payment_confirmed)
        }));

        console.log(`Facility restore: facility=${facilityName} ` +
                    `coupons=${coupons.length} stubs=${surveyStubs.length}`);

        res.json({
            status: 'success',
            data: { coupons, surveyStubs }
        });
    } catch (error) {
        console.error('Error in facility restore:', error);
        res.status(500).json({
            status: 'error',
            message: 'Failed to build facility restore payload',
            error: process.env.NODE_ENV === 'development' ? error.message : undefined
        });
    }
});

module.exports = router;
