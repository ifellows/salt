const { exec } = require('child_process');
const fs = require('fs').promises;
const path = require('path');
const uuid = require('uuid');
const { runAsync, getAsync } = require('../models/database');

class ReportExecutor {
    constructor() {
        this.reportsDir = path.join(process.cwd(), 'data', 'reports');
        this.tempDir = path.join(this.reportsDir, 'temp');
        this.runsDir = path.join(this.reportsDir, 'runs');
        this.sourcesDir = path.join(this.reportsDir, 'sources');
    }

    /**
     * Check if Quarto is available on the system
     */
    async checkQuartoAvailability() {
        return new Promise((resolve) => {
            exec('quarto --version', (error, stdout, stderr) => {
                if (error) {
                    resolve({ available: false, version: null });
                } else {
                    resolve({ available: true, version: stdout.trim() });
                }
            });
        });
    }

    /**
     * Execute a report and generate outputs in multiple formats
     * @param {number} reportId - The report ID from database
     * @param {string} runType - 'manual' or 'scheduled'
     * @param {number} userId - User ID who triggered the execution
     * @returns {Promise<string>} - Run ID of the execution
     */
    async executeReport(reportId, runType = 'manual', userId = null) {
        const runId = uuid.v4();
        const tempRunDir = path.join(this.tempDir, runId);
        const finalRunDir = path.join(this.runsDir, runId);

        let runRecordId;

        try {
            // 0. Check if Quarto is available
            const quartoCheck = await this.checkQuartoAvailability();
            if (!quartoCheck.available) {
                throw new Error('Quarto is not installed. Please install Quarto from https://quarto.org to generate reports.');
            }
            console.log(`Quarto version ${quartoCheck.version} detected`);

            // 1. Get report details from database
            const report = await getAsync(
                'SELECT * FROM reports WHERE id = ? AND is_active = 1',
                [reportId]
            );

            if (!report) {
                throw new Error(`Report ${reportId} not found or inactive`);
            }

            // 2. Create run record in database
            const result = await runAsync(
                `INSERT INTO report_runs (report_id, run_id, run_type, status, started_at)
                 VALUES (?, ?, ?, 'running', datetime('now'))`,
                [reportId, runId, runType]
            );

            // Get the actual ID since lastID might not work with our database wrapper
            const runRecord = await getAsync(
                'SELECT id FROM report_runs WHERE run_id = ?',
                [runId]
            );
            runRecordId = runRecord ? runRecord.id : null;

            if (!runRecordId) {
                throw new Error('Failed to create run record in database');
            }

            // 3. Create temporary working directory
            await fs.mkdir(tempRunDir, { recursive: true });

            // 4. Export data files to temp directory
            await this.exportDataFiles(tempRunDir);

            // 5. Create the .qmd file in temp directory
            const qmdPath = path.join(tempRunDir, 'report.qmd');
            await fs.writeFile(qmdPath, report.qmd_content, 'utf8');

            // 6. Execute Quarto to generate all formats
            const { logs, success } = await this.runQuarto(tempRunDir);

            // 7. Move outputs to permanent storage
            await fs.mkdir(finalRunDir, { recursive: true });

            // Copy all generated files
            const files = await fs.readdir(tempRunDir);
            for (const file of files) {
                if (file.endsWith('.html') || file.endsWith('.pdf') || file.endsWith('.docx')) {
                    await fs.copyFile(
                        path.join(tempRunDir, file),
                        path.join(finalRunDir, file)
                    );

                    // Record output file in database
                    const stats = await fs.stat(path.join(finalRunDir, file));
                    const fileType = path.extname(file).substring(1);

                    await runAsync(
                        `INSERT INTO report_outputs (run_id, file_type, file_path, file_size)
                         VALUES (?, ?, ?, ?)`,
                        [runId, fileType, path.join(finalRunDir, file), stats.size]
                    );
                }
            }

            // Save execution log
            const logPath = path.join(finalRunDir, 'execution.log');
            await fs.writeFile(logPath, logs, 'utf8');

            // 8. Update run record with results
            if (success) {
                try {
                    console.log(`Updating report run ${runRecordId} to completed status`);
                    await runAsync(
                        `UPDATE report_runs
                         SET status = 'completed',
                             completed_at = datetime('now'),
                             log_output = ?,
                             output_path = ?
                         WHERE id = ?`,
                        [logs, finalRunDir, runRecordId]
                    );
                    console.log(`Successfully updated report run ${runRecordId} to completed`);
                } catch (updateError) {
                    console.error(`Failed to update report run ${runRecordId} to completed:`, updateError);
                    // Try a simpler update as fallback
                    try {
                        await runAsync(
                            `UPDATE report_runs SET status = 'completed', completed_at = datetime('now') WHERE id = ?`,
                            [runRecordId]
                        );
                        console.log(`Fallback update succeeded for report run ${runRecordId}`);
                    } catch (fallbackError) {
                        console.error(`Even fallback update failed for report run ${runRecordId}:`, fallbackError);
                    }
                }
            } else {
                throw new Error('Quarto execution failed');
            }

            // 9. Clean up temp directory
            await this.cleanup(tempRunDir);

            return runId;

        } catch (error) {
            console.error(`Report execution error for run ${runId}:`, error);

            // Update run record with error - with additional error handling
            if (runRecordId) {
                try {
                    await runAsync(
                        `UPDATE report_runs
                         SET status = 'failed',
                             completed_at = datetime('now'),
                             error_message = ?,
                             log_output = ?
                         WHERE id = ?`,
                        [error.message, error.stack || '', runRecordId]
                    );
                } catch (dbError) {
                    console.error('Failed to update database with error status:', dbError);
                    // Try a simpler update as fallback
                    try {
                        await runAsync(
                            `UPDATE report_runs SET status = 'failed' WHERE id = ?`,
                            [runRecordId]
                        );
                    } catch (fallbackError) {
                        console.error('Even fallback database update failed:', fallbackError);
                    }
                }
            }

            // Clean up temp directory
            await this.cleanup(tempRunDir).catch(console.error);

            throw error;
        }
    }

    /**
     * Export data files in CSV format to the working directory
     */
    async exportDataFiles(workDir) {
        const DataExporter = require('./dataExporter');
        const exporter = new DataExporter();

        try {
            // Export long format
            const longData = await exporter.exportLongFormat();
            await fs.writeFile(path.join(workDir, 'data_long.csv'), longData, 'utf8');

            // Export wide format (text values)
            const wideTextData = await exporter.exportWideFormat('text');
            await fs.writeFile(path.join(workDir, 'data_wide.csv'), wideTextData, 'utf8');

            // Export wide format (numeric values)
            const wideNumericData = await exporter.exportWideFormat('numeric');
            await fs.writeFile(path.join(workDir, 'data_wide_numeric.csv'), wideNumericData, 'utf8');

        } catch (error) {
            console.error('Error exporting data files:', error);
            // Write empty CSVs as fallback
            await fs.writeFile(path.join(workDir, 'data_long.csv'), 'survey_id,participant_id,device_id,facility_name,completed_at,survey_version,data_version,question_id,variable,question_text,numeric_value,text_value,option_id,option_text\n', 'utf8');
            await fs.writeFile(path.join(workDir, 'data_wide.csv'), 'survey_id,participant_id,device_id,facility_name,meta_completed_at,meta_survey_version,meta_data_version,meta_is_test\n', 'utf8');
            await fs.writeFile(path.join(workDir, 'data_wide_numeric.csv'), 'survey_id,participant_id,device_id,facility_name,meta_completed_at,meta_survey_version,meta_data_version,meta_is_test\n', 'utf8');
        }
    }

    /**
     * Execute Quarto to render the report
     */
    runQuarto(workDir) {
        return new Promise((resolve) => {
            let logs = '';

            const quartoProcess = exec(
                'quarto render report.qmd --to html,pdf,docx',
                {
                    cwd: workDir,
                    timeout: 300000, // 5 minute timeout
                    env: { ...process.env, LANG: 'en_US.UTF-8' }
                },
                (error, stdout, stderr) => {
                    logs += stdout;
                    if (stderr) logs += '\nSTDERR:\n' + stderr;

                    if (error) {
                        logs += '\nERROR:\n' + error.message;
                        resolve({ logs, success: false });
                    } else {
                        resolve({ logs, success: true });
                    }
                }
            );

            // Capture real-time output
            if (quartoProcess.stdout) {
                quartoProcess.stdout.on('data', (data) => {
                    logs += data.toString();
                });
            }

            if (quartoProcess.stderr) {
                quartoProcess.stderr.on('data', (data) => {
                    logs += data.toString();
                });
            }
        });
    }

    /**
     * Clean up temporary directory
     */
    async cleanup(dir) {
        try {
            const files = await fs.readdir(dir);
            for (const file of files) {
                await fs.unlink(path.join(dir, file));
            }
            await fs.rmdir(dir);
        } catch (error) {
            console.error('Cleanup error:', error);
        }
    }

    /**
     * Get execution status for a run
     */
    async getRunStatus(runId) {
        return await getAsync(
            `SELECT r.*, rep.name as report_name
             FROM report_runs r
             JOIN reports rep ON r.report_id = rep.id
             WHERE r.run_id = ?`,
            [runId]
        );
    }

    /**
     * Get output files for a run
     */
    async getRunOutputs(runId) {
        return await getAsync(
            'SELECT * FROM report_outputs WHERE run_id = ? ORDER BY file_type',
            [runId]
        );
    }
}

module.exports = ReportExecutor;