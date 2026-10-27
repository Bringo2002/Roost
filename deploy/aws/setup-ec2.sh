#!/usr/bin/env bash
# ==============================================================================
# Roost Backend EC2 Bootstrap Script (Amazon Linux 2023)
# ==============================================================================
# This script provisions an Amazon Linux 2023 EC2 instance to run the Roost
# Spring Boot 4.1 / Java 21 backend application behind an Nginx reverse proxy
# with systemd process management and Let's Encrypt SSL support.
#
# Requirements:
# - Amazon Linux 2023
# - Must be run as root (or with sudo)
# - Idempotent: safe to run multiple times without unintended side-effects
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# 1. Root Check
# ------------------------------------------------------------------------------
if [[ "${EUID}" -ne 0 ]]; then
    echo "ERROR: This script must be run as root. Please run with sudo:" >&2
    echo "       sudo $0" >&2
    exit 1
fi

echo "================================================================="
echo " Starting Roost EC2 Bootstrap for Amazon Linux 2023"
echo " Date: $(date -u +"%Y-%m-%d %H:%M:%SZ")"
echo "================================================================="

# ------------------------------------------------------------------------------
# 2. System Update
# ------------------------------------------------------------------------------
echo "--> Updating system packages via dnf..."
dnf update -y

# ------------------------------------------------------------------------------
# 3. Package Installation
# ------------------------------------------------------------------------------
# Packages:
# - java-21-amazon-corretto-headless: Java 21 runtime optimized for Amazon Linux
# - nginx: Reverse proxy and web server
# - certbot: Let's Encrypt client
# - python3-certbot-nginx: Certbot Nginx plugin for automated SSL management
echo "--> Installing dependencies (Corretto 21 headless, Nginx, Certbot)..."
dnf install -y \
    java-21-amazon-corretto-headless \
    nginx \
    certbot \
    python3-certbot-nginx

# ------------------------------------------------------------------------------
# 4. System User Creation
# ------------------------------------------------------------------------------
# Create the unprivileged 'roost' system user and group if they don't already exist.
# - No login shell (/sbin/nologin)
# - System account (--system)
echo "--> Checking/creating system user 'roost'..."
if id -u roost >/dev/null 2>&1; then
    echo "    User 'roost' already exists. Skipping creation."
else
    # Determine nologin shell path (/sbin/nologin or /usr/sbin/nologin)
    NOLOGIN_SHELL="/sbin/nologin"
    if [[ ! -x "${NOLOGIN_SHELL}" ]] && [[ -x "/usr/sbin/nologin" ]]; then
        NOLOGIN_SHELL="/usr/sbin/nologin"
    fi

    useradd --system \
            --user-group \
            --no-create-home \
            --shell "${NOLOGIN_SHELL}" \
            --comment "Roost Application Service User" \
            roost
    echo "    Created system user and group 'roost'."
fi

# ------------------------------------------------------------------------------
# 5. Directory Creation & Permissions
# ------------------------------------------------------------------------------
# /opt/roost     : Application JAR and deployment artifacts
# /etc/roost     : Production environment variables and secrets (restricted read)
# /var/log/roost : Application log files (app.log)
echo "--> Ensuring application directories exist with proper permissions..."
mkdir -p /opt/roost
mkdir -p /etc/roost
mkdir -p /var/log/roost

# Set ownership
chown -R roost:roost /opt/roost
chown -R roost:roost /var/log/roost
# /etc/roost contains sensitive database & API secrets; allow root and roost read
chown -R root:roost /etc/roost

# Set permissions
chmod 755 /opt/roost
chmod 755 /var/log/roost
chmod 750 /etc/roost

echo "    Directories configured:"
echo "    - /opt/roost     (owned by roost:roost, 755)"
echo "    - /var/log/roost (owned by roost:roost, 755)"
echo "    - /etc/roost     (owned by root:roost, 750)"

# ------------------------------------------------------------------------------
# 6. Nginx Service Management
# ------------------------------------------------------------------------------
echo "--> Enabling and starting Nginx service..."
systemctl enable nginx
if systemctl is-active --quiet nginx; then
    echo "    Nginx is already running. Reloading configuration..."
    systemctl reload nginx
else
    systemctl start nginx
    echo "    Nginx started successfully."
fi

# ------------------------------------------------------------------------------
# 7. Summary and Verification
# ------------------------------------------------------------------------------
echo ""
echo "================================================================="
echo " Bootstrap Completed Successfully!"
echo "================================================================="
echo "Installed Components:"
echo "  - Java Runtime:"
java -version 2>&1 | sed 's/^/      /'
echo "  - Nginx Status: $(systemctl is-active nginx) (version: $(nginx -v 2>&1 | cut -d'/' -f2))"
echo "  - Certbot Version: $(certbot --version 2>&1 | awk '{print $2}')"
echo "  - Service User: $(id roost)"
echo ""
echo "Next Steps:"
echo "  1. Copy 'roost.service' to /etc/systemd/system/roost.service"
echo "     sudo cp roost.service /etc/systemd/system/roost.service"
echo "     sudo systemctl daemon-reload"
echo ""
echo "  2. Populate production secrets in /etc/roost/env (based on env.template)"
echo "     sudo cp env.template /etc/roost/env"
echo "     sudo chmod 600 /etc/roost/env"
echo "     sudo chown roost:roost /etc/roost/env"
echo ""
echo "  3. Configure Nginx reverse proxy:"
echo "     sudo cp nginx-roost.conf /etc/nginx/conf.d/roost.conf"
echo "     sudo nginx -t && sudo systemctl reload nginx"
echo ""
echo "  4. Deploy the application JAR using deploy.sh:"
echo "     sudo ./deploy.sh"
echo ""
echo "  5. Set up SSL with Let's Encrypt (once your DNS domain points to this EC2):"
echo "     sudo certbot --nginx -d yourdomain.com"
echo "================================================================="
