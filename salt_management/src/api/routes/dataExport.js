const express = require('express');
const router = express.Router();
const { allAsync, getAsync } = require('../../models/database');
const { requireAdmin } = require('../../middleware/auth');
const DataExporter = require('../../services/dataExporter');

/**
 * Export survey data in long format (one row per variable)
 * GET /api/admin/export/long
 */
router.get('/export/long', requireAdmin, async (req, res) => {
    try {
        const { preview } = req.query;
        const exporter = new DataExporter();

        // For now, DataExporter doesn't support filtering, so we'll use the full export
        // TODO: Add filtering support to DataExporter
        const csvData = await exporter.exportLongFormat();

        if (preview === 'true') {
            // Parse CSV for preview
            const lines = csvData.split('\n');
            const headers = lines[0].split(',');
            const dataLines = lines.slice(1, 101); // First 100 rows

            const rows = dataLines.map(line => {
                const values = line.match(/(".*?"|[^,]+)/g) || [];
                const row = {};
                headers.forEach((header, index) => {
                    let value = values[index] || '';
                    // Remove quotes if present
                    if (value.startsWith('"') && value.endsWith('"')) {
                        value = value.slice(1, -1).replace(/""/g, '"');
                    }
                    row[header] = value;
                });
                return row;
            });

            res.json({
                count: lines.length - 1,
                preview: rows,
                columns: headers
            });
        } else {
            // Set response headers for CSV download
            const date = new Date().toISOString().split('T')[0];
            res.setHeader('Content-Type', 'text/csv; charset=utf-8');
            res.setHeader('Content-Disposition', `attachment; filename="salt_export_long_${date}.csv"`);

            // Add UTF-8 BOM for Excel compatibility
            res.write('\ufeff');
            res.write(csvData);
            res.end();
        }

    } catch (error) {
        console.error('Error exporting data in long format:', error);
        res.status(500).json({ error: 'Failed to export data' });
    }
});

/**
 * Export survey data in wide format (one row per survey) - SIMPLIFIED VERSION
 * GET /api/admin/export/wide
 */
router.get('/export/wide', requireAdmin, async (req, res) => {
    try {
        const { preview, valueType } = req.query;
        const exporter = new DataExporter();

        // Use the shared exporter
        const csvData = await exporter.exportWideFormat(valueType || 'numeric');

        if (preview === 'true') {
            // Parse CSV for preview
            const lines = csvData.split('\n');
            const headers = lines[0].split(',');
            const dataLines = lines.slice(1, 11); // First 10 rows

            const rows = dataLines.map(line => {
                const values = line.match(/(".*?"|[^,]+)/g) || [];
                const row = {};
                headers.forEach((header, index) => {
                    let value = values[index] || '';
                    // Remove quotes if present
                    if (value.startsWith('"') && value.endsWith('"')) {
                        value = value.slice(1, -1).replace(/""/g, '"');
                    }
                    row[header] = value;
                });
                return row;
            });

            res.json({
                count: lines.length - 1,
                preview: rows,
                columns: headers
            });
        } else {
            // Set response headers for CSV download
            const date = new Date().toISOString().split('T')[0];
            res.setHeader('Content-Type', 'text/csv; charset=utf-8');
            res.setHeader('Content-Disposition', `attachment; filename="salt_export_wide_${date}.csv"`);

            // Add UTF-8 BOM for Excel compatibility
            res.write('\ufeff');
            res.write(csvData);
            res.end();
        }

    } catch (error) {
        console.error('Error exporting data in wide format:', error);
        res.status(500).json({ error: 'Failed to export data' });
    }
});

module.exports = router;