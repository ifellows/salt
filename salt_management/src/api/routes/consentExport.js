const express = require('express');
const router = express.Router();
const { requireAdmin } = require('../../middleware/auth');
const ConsentPdfGenerator = require('../../services/consentPdfGenerator');

/**
 * Export consent records as PDF
 * GET /api/admin/export/consents
 */
router.get('/export/consents', requireAdmin, async (req, res) => {
    try {
        const generator = new ConsentPdfGenerator();
        const doc = await generator.generate();

        const date = new Date().toISOString().split('T')[0];
        res.setHeader('Content-Type', 'application/pdf');
        res.setHeader('Content-Disposition', `attachment; filename="salt_consents_${date}.pdf"`);

        doc.pipe(res);
    } catch (error) {
        console.error('Error exporting consent records:', error);
        res.status(500).json({ error: 'Failed to export consent records' });
    }
});

module.exports = router;
