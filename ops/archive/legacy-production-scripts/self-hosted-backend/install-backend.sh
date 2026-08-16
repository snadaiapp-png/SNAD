#!/usr/bin/env bash
# ============================================================
# SANAD Platform — Backend Installation Script
# ============================================================
# Installs the backend as a systemd service that:
#   - Starts automatically on boot
#   - Restarts automatically on crash
#   - Runs in the background (no SSH session needed)
#
# Usage (on the production server, as root or with sudo):
#   sudo bash scripts/production/install-backend.sh
# ============================================================
set -euo pipefail

INSTALL_DIR="/opt/sanad-platform"
JAR_NAME="sanad-platform.jar"
SERVICE_NAME="sanad-backend"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
LOG_DIR="/var/log/sanad"
USER_NAME="sanad"

echo "=== SANAD Backend Installation ==="

# 1. Create sanad user if not exists
if ! id "$USER_NAME" &>/dev/null; then
    echo "→ Creating user: $USER_NAME"
    useradd --system --no-create-home --shell /bin/false "$USER_NAME"
fi

# 2. Create directories
echo "→ Creating directories"
mkdir -p "$INSTALL_DIR"
mkdir -p "$LOG_DIR"
chown "$USER_NAME":"$USER_NAME" "$INSTALL_DIR"
chown "$USER_NAME":"$USER_NAME" "$LOG_DIR"

# 3. Check if JAR exists
if [ ! -f "${INSTALL_DIR}/${JAR_NAME}" ]; then
    echo "✗ ERROR: ${INSTALL_DIR}/${JAR_NAME} not found!"
    echo "  Please copy the JAR file first:"
    echo "  scp apps/sanad-platform/target/sanad-platform-*.jar server:/opt/sanad-platform/sanad-platform.jar"
    exit 1
fi

# 4. Install systemd service
echo "→ Installing systemd service"
cp scripts/production/sanad-backend.service "$SERVICE_FILE"
chmod 644 "$SERVICE_FILE"

# 5. Reload systemd and enable service
echo "→ Enabling service (starts on boot)"
systemctl daemon-reload
systemctl enable "$SERVICE_NAME"

# 6. Start the service
echo "→ Starting service"
systemctl start "$SERVICE_NAME"

# 7. Wait and check status
sleep 5
echo ""
echo "=== Service Status ==="
systemctl status "$SERVICE_NAME" --no-pager || true

echo ""
echo "=== Done ==="
echo "Backend is now running as a systemd service."
echo "It will:"
echo "  ✓ Start automatically on boot"
echo "  ✓ Restart automatically on crash (after 10s)"
echo "  ✓ Run in the background (no SSH needed)"
echo ""
echo "Commands:"
echo "  Status:   sudo systemctl status sanad-backend"
echo "  Stop:     sudo systemctl stop sanad-backend"
echo "  Start:    sudo systemctl start sanad-backend"
echo "  Restart:  sudo systemctl restart sanad-backend"
echo "  Logs:     sudo journalctl -u sanad-backend -f"
echo "  Health:   curl http://localhost:8080/actuator/health"
