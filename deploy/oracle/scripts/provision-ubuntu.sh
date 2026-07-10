#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script with sudo: sudo bash provision-ubuntu.sh" >&2
  exit 1
fi

DEPLOY_USER="${SUDO_USER:-ubuntu}"
INSTALL_DIR="/opt/snad"

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates curl gnupg git jq ufw fail2ban unattended-upgrades

# Install Docker Engine from Docker's official Ubuntu repository.
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
usermod -aG docker "${DEPLOY_USER}"

# Host firewall. Oracle VCN/NSG rules must independently allow only 22, 80 and 443.
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

systemctl enable --now fail2ban
systemctl enable --now unattended-upgrades || true

mkdir -p "${INSTALL_DIR}" /var/backups/snad/postgres
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${INSTALL_DIR}" /var/backups/snad
chmod 750 /var/backups/snad /var/backups/snad/postgres

cat <<EOF
Provisioning completed.

Next steps:
1. Sign out and sign in again so Docker group membership applies.
2. Clone SNAD into ${INSTALL_DIR}.
3. Copy deploy/oracle/.env.oracle.example to deploy/oracle/.env and replace every placeholder.
4. Run the preflight and deployment commands from deploy/oracle/README.md.
EOF
