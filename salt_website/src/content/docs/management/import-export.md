---
title: Import & Export Surveys
description: Exporting a survey definition as a JSON bundle and importing it on another server.
---

SALT can export a complete survey definition as a single JSON file and import it on another server. This is useful for sharing surveys between installations, creating backups of survey definitions, or transferring a survey from a test environment to production.

## What is included in a survey export

An exported survey bundle contains:

- Survey metadata (name, version, settings)
- All sections
- All questions with their options
- All skip logic, validation scripts, and skip-to configurations
- System messages (ineligibility message and others)
- Rapid test configurations
- Audio files — embedded as base64 in the `audio_files_json` field

The export does **not** include collected response data. It is a definition-only export.

## Exporting a survey

1. Go to **Surveys**
2. Click the export/download icon next to the survey you want to export
3. Save the JSON file

The exported file contains a `schema_version` field set to `1`.

## Importing a survey

1. Go to **Surveys**
2. Click **Import Survey**
3. Select the JSON file

### What happens on import

- Import always creates a **new, inactive survey** — it never overwrites an existing survey
- If the imported survey's name already exists in the database, the version number is automatically incremented (e.g. `Short MSM Survey v1` becomes `Short MSM Survey v2`)
- All questions, options, skip logic, messages, and audio are imported
- The import is fully transactional — if any part fails, nothing is written

After importing, review the survey in the editor and activate it when ready.

## Version field

The `schema_version` field in the export JSON must be `1`. Exports from future versions of SALT may have a higher schema version; those exports will not be importable into older server installations.

## Audio in exports

Audio files are embedded as base64-encoded strings in the `audio_files_json` field. This makes the export self-contained but also large — a survey with extensive multi-language audio may produce an export file that is several hundred megabytes. Ensure your browser and server both allow uploads of sufficient size (the server limit is 50 MB by default; large audio surveys may require adjusting the nginx `client_max_body_size` and the Node.js body parser limit).
