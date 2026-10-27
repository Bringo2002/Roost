#!/usr/bin/env bash
# ==============================================================================
# Roost Application Deployment Script
# ==============================================================================
# Deploys a new Spring Boot application JAR to /opt/roost/app.jar, sets correct
# permissions, restarts the systemd service, and validates health on port 8080.
#
# Usage:
#   sudo ./deploy.sh [PATH_TO_JAR]
#
# Example:
#   sudo ./deploy.sh
#   sudo ./deploy.sh /path/to/custom-roost.jar
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# 1. Root Check
# ------------------------------------------------------------------------------
if [[ "${EUID}" -ne 0 ]]; then
    echo "ERROR: This script must be run as root. Please run with sudo:" >&2
    echo "       sudo $0 [path_to_jar]" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="/opt/roost"
TARGET_JAR="${TARGET_DIR}/app.jar"

# Default to Maven target directory relative to this script
DEFAULT_JAR="${SCRIPT_DIR}/../../target/roost-0.0.1-SNAPSHOT.jar"
SOURCE_JAR="${1:-"${DEFAULT_JAR}"}"

echo "================================================================="
echo " Starting Roost Application Deployment"
echo " Timestamp: $(date -u +"%Y-%m-%d %H:%M:%SZ")"
echo " Source JAR: ${SOURCE_JAR}"
echo " Target JAR: ${TARGET_JAR}"
echo "================================================================="

# ------------------------------------------------------------------------------
# 2. Validate Source JAR
# ------------------------------------------------------------------------------
if [[ ! -f "${SOURCE_JAR}" ]]; then
    echo "ERROR: Source JAR file does not exist at '${SOURCE_JAR}'." >&2
    echo "Hint: Build the JAR first using: ./mvnw clean package -DskipTests" >&2
    exit 1
fi

JAR_SIZE=$(du -h "${SOURCE_JAR}" | cut -f1)
echo "--> Found source JAR (${JAR_SIZE})"

# ------------------------------------------------------------------------------
# 3. Copy JAR and Set Permissions
# ------------------------------------------------------------------------------
mkdir -p "${TARGET_DIR}"
echo "--> Copying JAR to ${TARGET_JAR}..."
# Copy to temp file first for atomic swap
cp "${SOURCE_JAR}" "${TARGET_JAR}.tmp"
mv -f "${TARGET_JAR}.tmp" "${TARGET_JAR}"

echo "--> Setting ownership and permissions (roost:roost, 644)..."
chown roost:roost "${TARGET_JAR}"
chmod 644 "${TARGET_JAR}"

# ------------------------------------------------------------------------------
# 4. Restart Service
# ------------------------------------------------------------------------------
echo "--> Reloading systemd daemon and restarting 'roost' service..."
systemctl daemon-reload
systemctl restart roost

# ------------------------------------------------------------------------------
# 5. Wait for Startup and Health Check (up to 30 seconds)
# ------------------------------------------------------------------------------
echo "--> Waiting for application to start on port 8080 (max 30 seconds)..."
MAX_WAIT_SECONDS=30
START_TIME=$(date +%s)
APP_READY=0

for ((i=1; i<=MAX_WAIT_SECONDS; i++)); do
    # Check if systemd unit died
    if ! systemctl is-active --quiet roost; then
        echo ""
        echo "ERROR: The 'roost' systemd service stopped unexpectedly!" >&2
        break
    fi

    # Query port 8080 via HTTP; any HTTP response code (200, 401, 404, etc.)
    # confirms the embedded Tomcat container is initialized and accepting requests.
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -m 2 http://127.0.0.1:8080/ || true)
    if [[ -n "${HTTP_STATUS}" ]] && [[ "${HTTP_STATUS}" != "000" ]]; then
        APP_READY=1
        break
    fi

    # Fallback TCP socket check
    if (echo > /dev/tcp/127.0.0.1/8080) 2>/dev/null; then
        APP_READY=1
        break
    fi

    sleep 1
    echo -n "."
done
echo ""

# ------------------------------------------------------------------------------
# 6. Report Success or Failure
# ------------------------------------------------------------------------------
if [[ "${APP_READY}" -eq 1 ]]; then
    ELAPSED=$(( $(date +%s) - START_TIME ))
    echo "================================================================="
    echo " SUCCESS: Roost Backend deployed and listening on port 8080!"
    echo " Time taken:   ${ELAPSED} seconds"
    echo " Service:      $(systemctl is-active roost)"
    echo " Log Output:   /var/log/roost/app.log"
    echo "================================================================="
    exit 0
else
    echo "================================================================="
    echo " FAILURE: Application failed to start within ${MAX_WAIT_SECONDS} seconds."
    echo "-----------------------------------------------------------------"
    echo " Recent systemd journal logs:"
    journalctl -u roost -n 25 --no-pager || true
    if [[ -f /var/log/roost/app.log ]]; then
        echo "-----------------------------------------------------------------"
        echo " Tail of /var/log/roost/app.log:"
        tail -n 25 /var/log/roost/app.log || true
    fi
    echo "================================================================="
    exit 1
fi
