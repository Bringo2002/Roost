#!/usr/bin/env bash
# ==============================================================================
# Roost Database Migration Helper Script
# ==============================================================================
# Restores a PostgreSQL dump file into the target database (AWS RDS).
#
# Two-step workflow (recommended):
#   1. On your LAPTOP (which can reach Railway):
#      pg_dump -h <RAILWAY_HOST> -p <PORT> -U postgres -d railway \
#        -Fc --no-owner --no-acl -f roost.dump
#      scp -i ~/.ssh/roost-key.pem roost.dump ec2-user@13.62.233.120:~/
#
#   2. On the EC2 SERVER (which can reach RDS):
#      ./migrate-db.sh --dump-file ~/roost.dump \
#        --target-host <RDS_ENDPOINT> --target-user roost_admin --target-db roost
#
# Live-pull workflow (if EC2 can reach the source DB directly):
#   ./migrate-db.sh \
#     --source-host <HOST> --source-port <PORT> --source-user <USER> --source-db <DB> \
#     --target-host <RDS_ENDPOINT> --target-user roost_admin --target-db roost
#
# Requirements:
#   - pg_restore (and pg_dump if doing a live pull) installed
#   - TARGET_PGPASSWORD (or PGPASSWORD) exported for the target database
#   - SOURCE_PGPASSWORD exported if doing a live pull
# ==============================================================================

set -euo pipefail

# Default values
DUMP_FILE=""
SOURCE_HOST=""
SOURCE_PORT="5432"
SOURCE_USER=""
SOURCE_DB=""
TARGET_HOST=""
TARGET_PORT="5432"
TARGET_USER=""
TARGET_DB=""
DRY_RUN=0

print_usage() {
    cat <<'EOF'
Usage: migrate-db.sh [OPTIONS]

Mode 1 — Restore from dump file (recommended):
  --dump-file FILE        Path to a pg_dump custom-format (.dump) file
  --target-host HOST      Target RDS endpoint
  --target-user USER      Target database username
  --target-db   NAME      Target database name
  [--target-port PORT]    Target port (default: 5432)

Mode 2 — Live pull from source and restore:
  --source-host HOST      Source PostgreSQL host (e.g. Railway)
  --source-user USER      Source username
  --source-db   NAME      Source database name
  [--source-port PORT]    Source port (default: 5432)
  --target-host HOST      Target RDS endpoint
  --target-user USER      Target database username
  --target-db   NAME      Target database name
  [--target-port PORT]    Target port (default: 5432)

Options:
  --dry-run               Export only (Mode 2) or validate dump file only (Mode 1)
  -h, --help              Show this help

Environment Variables:
  TARGET_PGPASSWORD       Password for the target (RDS) database
  SOURCE_PGPASSWORD       Password for the source database (Mode 2 only)
  PGPASSWORD              Fallback if the above are not set
EOF
}

# ------------------------------------------------------------------------------
# 1. Parse Arguments
# ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dump-file)       DUMP_FILE="$2";    shift 2 ;;
        --dump-file=*)     DUMP_FILE="${1#*=}"; shift ;;
        --source-host)     SOURCE_HOST="$2";  shift 2 ;;
        --source-host=*)   SOURCE_HOST="${1#*=}"; shift ;;
        --source-port)     SOURCE_PORT="$2";  shift 2 ;;
        --source-port=*)   SOURCE_PORT="${1#*=}"; shift ;;
        --source-user)     SOURCE_USER="$2";  shift 2 ;;
        --source-user=*)   SOURCE_USER="${1#*=}"; shift ;;
        --source-db)       SOURCE_DB="$2";    shift 2 ;;
        --source-db=*)     SOURCE_DB="${1#*=}"; shift ;;
        --target-host)     TARGET_HOST="$2";  shift 2 ;;
        --target-host=*)   TARGET_HOST="${1#*=}"; shift ;;
        --target-port)     TARGET_PORT="$2";  shift 2 ;;
        --target-port=*)   TARGET_PORT="${1#*=}"; shift ;;
        --target-user)     TARGET_USER="$2";  shift 2 ;;
        --target-user=*)   TARGET_USER="${1#*=}"; shift ;;
        --target-db)       TARGET_DB="$2";    shift 2 ;;
        --target-db=*)     TARGET_DB="${1#*=}"; shift ;;
        --dry-run)         DRY_RUN=1;         shift ;;
        -h|--help)         print_usage;       exit 0 ;;
        *) echo "ERROR: Unknown parameter: $1" >&2; print_usage; exit 1 ;;
    esac
done

# ------------------------------------------------------------------------------
# 2. Determine Mode and Validate
# ------------------------------------------------------------------------------
MODE=""
if [[ -n "${DUMP_FILE}" ]]; then
    MODE="file"
    if [[ ! -f "${DUMP_FILE}" ]]; then
        echo "ERROR: Dump file not found: ${DUMP_FILE}" >&2
        exit 1
    fi
elif [[ -n "${SOURCE_HOST}" ]]; then
    MODE="live"
    command -v pg_dump >/dev/null 2>&1 || {
        echo "ERROR: pg_dump is not installed. Install with: sudo dnf install -y postgresql16" >&2
        exit 1
    }
    if [[ -z "${SOURCE_USER}" || -z "${SOURCE_DB}" ]]; then
        echo "ERROR: --source-user and --source-db are required for live pull." >&2
        exit 1
    fi
    SOURCE_PASS="${SOURCE_PGPASSWORD:-${PGPASSWORD:-}}"
    if [[ -z "${SOURCE_PASS}" ]]; then
        echo "ERROR: Set SOURCE_PGPASSWORD or PGPASSWORD for the source database." >&2
        exit 1
    fi
else
    echo "ERROR: Provide either --dump-file or --source-host." >&2
    print_usage
    exit 1
fi

if [[ "${DRY_RUN}" -eq 0 ]]; then
    command -v pg_restore >/dev/null 2>&1 || {
        echo "ERROR: pg_restore is not installed. Install with: sudo dnf install -y postgresql16" >&2
        exit 1
    }
    if [[ -z "${TARGET_HOST}" || -z "${TARGET_USER}" || -z "${TARGET_DB}" ]]; then
        echo "ERROR: --target-host, --target-user, and --target-db are required." >&2
        exit 1
    fi
    TARGET_PASS="${TARGET_PGPASSWORD:-${PGPASSWORD:-}}"
    if [[ -z "${TARGET_PASS}" ]]; then
        echo "ERROR: Set TARGET_PGPASSWORD or PGPASSWORD for the target database." >&2
        exit 1
    fi
fi

# Also need psql for row count verification
command -v psql >/dev/null 2>&1 || {
    echo "WARNING: psql is not installed — row count verification will be skipped." >&2
    echo "         Install with: sudo dnf install -y postgresql16" >&2
    HAS_PSQL=0
}
HAS_PSQL=${HAS_PSQL:-1}

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# ------------------------------------------------------------------------------
# 3. Export (live pull mode only)
# ------------------------------------------------------------------------------
if [[ "${MODE}" == "live" ]]; then
    DUMP_FILE="/tmp/roost_migration_${TIMESTAMP}.dump"
    echo "================================================================="
    echo " STEP 1: Exporting source database"
    echo "   Source: ${SOURCE_USER}@${SOURCE_HOST}:${SOURCE_PORT}/${SOURCE_DB}"
    echo "   Output: ${DUMP_FILE}"
    echo "================================================================="

    PGPASSWORD="${SOURCE_PASS}" pg_dump \
        -h "${SOURCE_HOST}" \
        -p "${SOURCE_PORT}" \
        -U "${SOURCE_USER}" \
        -d "${SOURCE_DB}" \
        --format=custom \
        --no-owner \
        --no-acl \
        --verbose \
        --file="${DUMP_FILE}"

    DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)
    echo "--> Export completed: ${DUMP_FILE} (${DUMP_SIZE})"
fi

# ------------------------------------------------------------------------------
# 4. Validate Dump File
# ------------------------------------------------------------------------------
DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)
echo ""
echo "================================================================="
echo " Dump file: ${DUMP_FILE} (${DUMP_SIZE})"
echo " Contents (pg_restore --list, first 30 lines):"
echo "================================================================="
pg_restore --list "${DUMP_FILE}" 2>/dev/null | head -30
echo "  ... (truncated)"

if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo ""
    echo "================================================================="
    echo " DRY RUN COMPLETE — no changes made to any target database."
    echo " Dump file: ${DUMP_FILE} (${DUMP_SIZE})"
    echo "================================================================="
    exit 0
fi

# ------------------------------------------------------------------------------
# 5. Confirmation
# ------------------------------------------------------------------------------
echo ""
echo "================================================================="
echo " WARNING: You are about to restore into:"
echo "   Host:     ${TARGET_HOST}:${TARGET_PORT}"
echo "   Database: ${TARGET_DB}"
echo "   User:     ${TARGET_USER}"
echo "   From:     ${DUMP_FILE} (${DUMP_SIZE})"
echo ""
echo " This will ADD tables and data. Existing tables with the same"
echo " names may cause errors (which pg_restore reports but continues)."
echo "================================================================="
read -r -p "Proceed with restore? [y/N]: " CONFIRM
if [[ ! "${CONFIRM}" =~ ^[Yy]$ ]]; then
    echo "Aborted. Dump file preserved at: ${DUMP_FILE}"
    exit 0
fi

# ------------------------------------------------------------------------------
# 6. Restore into Target
# ------------------------------------------------------------------------------
echo "--> Restoring into ${TARGET_DB}@${TARGET_HOST}..."

set +e
PGPASSWORD="${TARGET_PASS}" pg_restore \
    -h "${TARGET_HOST}" \
    -p "${TARGET_PORT}" \
    -U "${TARGET_USER}" \
    -d "${TARGET_DB}" \
    --no-owner \
    --no-acl \
    --verbose \
    "${DUMP_FILE}" 2>&1 | tee "/tmp/roost_restore_${TIMESTAMP}.log"
RESTORE_STATUS=${PIPESTATUS[0]}
set -e

echo ""
if [[ ${RESTORE_STATUS} -ne 0 ]]; then
    echo "WARNING: pg_restore exited with code ${RESTORE_STATUS}."
    echo "This is normal if tables already existed (e.g. from Flyway)."
    echo "Review the log: /tmp/roost_restore_${TIMESTAMP}.log"
fi

# ------------------------------------------------------------------------------
# 7. Row Count Verification
# ------------------------------------------------------------------------------
if [[ "${HAS_PSQL}" -eq 1 ]]; then
    echo ""
    echo "================================================================="
    echo " Row count verification on target database"
    echo "================================================================="

    PGPASSWORD="${TARGET_PASS}" psql \
        -h "${TARGET_HOST}" \
        -p "${TARGET_PORT}" \
        -U "${TARGET_USER}" \
        -d "${TARGET_DB}" \
        --no-psqlrc \
        -c "
SELECT
    schemaname || '.' || relname AS table_name,
    n_live_tup AS row_count
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;
"
    echo ""
    echo "Compare these counts with the source database to verify completeness."
    echo "If counts look low, run ANALYZE first:  ANALYZE;"
fi

# ------------------------------------------------------------------------------
# 8. Done
# ------------------------------------------------------------------------------
echo ""
echo "================================================================="
echo " RESTORE COMPLETE"
echo "   Target:    ${TARGET_USER}@${TARGET_HOST}:${TARGET_PORT}/${TARGET_DB}"
echo "   Dump:      ${DUMP_FILE}"
echo "   Log:       /tmp/roost_restore_${TIMESTAMP}.log"
echo "================================================================="
echo ""
echo "Next steps:"
echo "  1. Verify row counts match the source database"
echo "  2. Run the URL rewrite script (rewrite-media-urls.sql)"
echo "  3. Build and deploy the JAR: sudo bash deploy.sh ~/roost-0.0.1-SNAPSHOT.jar"
