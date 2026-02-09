const PDFDocument = require('pdfkit');
const fsSync = require('fs');
const fs = require('fs').promises;
const path = require('path');
const { allAsync, getAsync } = require('../models/database');

// GNU Unifont - broadest Unicode coverage, bundled in project
const FONT_SEARCH_PATHS = [
    path.join(__dirname, '..', '..', 'fonts', 'unifont.otf'),
];

function findUnicodeFont() {
    for (const fontPath of FONT_SEARCH_PATHS) {
        if (fsSync.existsSync(fontPath)) {
            return fontPath;
        }
    }
    return null;
}

class ConsentPdfGenerator {
    /**
     * Generate a PDF containing all consent records.
     * Returns a PDFKit document (readable stream) for piping to an HTTP response.
     */
    async generate() {
        // Query all completed surveys that have a consent signature
        const records = await allAsync(`
            SELECT
                cs.participant_id,
                cs.completed_at,
                cs.uploaded_at,
                cs.consent_signature_path,
                cs.json_file_path,
                cs.survey_id,
                cs.language,
                f.name as facility_name
            FROM completed_surveys cs
            LEFT JOIN facilities f ON cs.facility_id = f.id
            WHERE cs.consent_signature_path IS NOT NULL
            ORDER BY cs.completed_at
        `);

        const doc = new PDFDocument({ size: 'A4', margin: 50 });

        // Register a Unicode font for multi-language support
        const unicodeFont = findUnicodeFont();
        if (unicodeFont) {
            doc.registerFont('Unicode', unicodeFont);
            doc.registerFont('Unicode-Bold', unicodeFont); // Same file, we'll fake bold with size
        }

        const bodyFont = unicodeFont ? 'Unicode' : 'Helvetica';
        const boldFont = unicodeFont ? 'Unicode' : 'Helvetica-Bold';

        // Title page
        doc.fontSize(24).font(boldFont).text('SALT Consent Records', { align: 'center' });
        doc.moveDown(2);
        doc.fontSize(12).font(bodyFont).text(`Generated: ${new Date().toISOString().split('T')[0]}`, { align: 'center' });
        doc.moveDown();
        doc.text(`Total consent records: ${records.length}`, { align: 'center' });

        // Render each consent record on its own page
        for (const record of records) {
            doc.addPage();

            // Subject ID
            doc.fontSize(14).font(boldFont).text('Subject ID');
            doc.fontSize(12).font(bodyFont).text(record.participant_id || 'Unknown');
            doc.moveDown();

            // Facility
            doc.fontSize(14).font(boldFont).text('Facility');
            doc.fontSize(12).font(bodyFont).text(record.facility_name || 'Unknown');
            doc.moveDown();

            // Consent text
            doc.fontSize(14).font(boldFont).text('Consent Text');
            const consentText = await this._getConsentText(record);
            doc.fontSize(10).font(bodyFont).text(consentText || 'No consent text available', {
                width: 495,
                lineGap: 2
            });
            doc.moveDown();

            // Signature image
            doc.fontSize(14).font(boldFont).text('Signature');
            try {
                const sigBuffer = Buffer.from(record.consent_signature_path, 'hex');
                doc.image(sigBuffer, { fit: [300, 100] });
            } catch (err) {
                doc.fontSize(10).font(bodyFont).text('(Unable to render signature image)');
            }
            doc.moveDown();

            // Date of consent
            doc.fontSize(14).font(boldFont).text('Date of Consent');
            doc.fontSize(12).font(bodyFont).text(record.completed_at || 'Unknown');
            doc.moveDown();

            // Date of upload
            doc.fontSize(14).font(boldFont).text('Date of Upload');
            doc.fontSize(12).font(bodyFont).text(record.uploaded_at || 'Unknown');
        }

        doc.end();
        return doc;
    }

    /**
     * Try to extract the consent text from the uploaded JSON file.
     * Falls back to survey_messages table, then to a placeholder.
     */
    async _getConsentText(record) {
        // Try reading from the JSON file first
        if (record.json_file_path) {
            try {
                const raw = await fs.readFile(record.json_file_path, 'utf-8');
                const data = JSON.parse(raw);
                const surveyData = data.survey_data || data;
                if (surveyData.consentMessageText) {
                    return surveyData.consentMessageText;
                }
            } catch (err) {
                // File missing or unreadable, fall through
            }
        }

        // Fallback: survey_messages table
        if (record.survey_id) {
            try {
                const lang = record.language || 'English';
                const msg = await getAsync(
                    `SELECT message_text FROM survey_messages
                     WHERE survey_id = ? AND message_key = 'consent_message' AND language = ?`,
                    [record.survey_id, lang]
                );
                if (msg && msg.message_text) {
                    return msg.message_text;
                }
                // Try without language filter
                const msgAny = await getAsync(
                    `SELECT message_text FROM survey_messages
                     WHERE survey_id = ? AND message_key = 'consent_message'
                     LIMIT 1`,
                    [record.survey_id]
                );
                if (msgAny && msgAny.message_text) {
                    return msgAny.message_text;
                }
            } catch (err) {
                // Table may not exist or no rows, fall through
            }
        }

        return null;
    }
}

module.exports = ConsentPdfGenerator;
