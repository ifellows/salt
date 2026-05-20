#!/bin/bash
#
# SALT Management Server entrypoint
#
# On every boot:
#   1. Ensure the data subdirs exist (host bind mounts may shadow the
#      pre-created tree from the image).
#   2. Auto-generate SESSION_SECRET if the operator didn't supply one.
#      Persist to /app/data/.session-secret so sessions survive restarts.
#      An explicit env var still wins.
#   3. Run the idempotent init-database.js — applies the full schema with
#      IF NOT EXISTS, seeds the demo facility + admin + sample survey + lab
#      tests only when the relevant tables are empty.
#   4. exec the main command (node src/app.js by default).

set -e

DATA_ROOT=/app/data

mkdir -p \
    "$DATA_ROOT/database" \
    "$DATA_ROOT/audit" \
    "$DATA_ROOT/files" \
    "$DATA_ROOT/uploads/surveys" \
    "$DATA_ROOT/uploads/recruitment_payments" \
    "$DATA_ROOT/uploads/labs" \
    "$DATA_ROOT/uploads/device_logs" \
    "$DATA_ROOT/reports/temp" \
    "$DATA_ROOT/reports/runs" \
    "$DATA_ROOT/reports/sources" \
    "$DATA_ROOT/reports/templates" \
    "$DATA_ROOT/surveys"

# --- SESSION_SECRET ---------------------------------------------------------
if [ -z "${SESSION_SECRET:-}" ]; then
    SECRET_FILE="$DATA_ROOT/.session-secret"
    if [ ! -f "$SECRET_FILE" ]; then
        echo "[entrypoint] SESSION_SECRET not provided; generating one and persisting to $SECRET_FILE"
        openssl rand -hex 32 > "$SECRET_FILE"
        chmod 600 "$SECRET_FILE"
    fi
    SESSION_SECRET="$(cat "$SECRET_FILE")"
    export SESSION_SECRET
fi

# --- Database init / upgrade ------------------------------------------------
# init-database.js is idempotent (CREATE TABLE IF NOT EXISTS everywhere,
# guarded INSERTs for seed data), so running on every boot is safe. Fresh
# deployments get the full schema + seeds; existing deployments are no-ops
# unless the image ships a newer schema with additional tables.
echo "[entrypoint] Running database init..."
node scripts/init-database.js

# --- Hand off to the app ----------------------------------------------------
echo "[entrypoint] Starting SALT server..."
exec "$@"
