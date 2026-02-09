const express = require('express');
const router = express.Router();
const { requireAdmin } = require('../../middleware/auth');
const PaymentExporter = require('../../services/paymentExporter');

/**
 * Export payment records as CSV
 * GET /api/admin/export/payments
 */
router.get('/export/payments', requireAdmin, async (req, res) => {
    try {
        const exporter = new PaymentExporter();
        const csvData = await exporter.exportCSV();

        const date = new Date().toISOString().split('T')[0];
        res.setHeader('Content-Type', 'text/csv; charset=utf-8');
        res.setHeader('Content-Disposition', `attachment; filename="salt_payments_${date}.csv"`);

        // UTF-8 BOM for Excel compatibility
        res.write('\ufeff');
        res.write(csvData);
        res.end();
    } catch (error) {
        console.error('Error exporting payment records:', error);
        res.status(500).json({ error: 'Failed to export payment records' });
    }
});

module.exports = router;
