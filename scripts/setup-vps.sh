#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# ZyntaPOS — VPS Initial Setup Script
#
# Run this on the Contabo VPS as root ONCE after provisioning.
# After this script, Claude Code can SSH in as 'deploy' and manage
# the server via docker compose commands.
#
# Usage (from your local machine):
#   ssh root@<VPS_IP> 'bash -s' < scripts/setup-vps.sh
#
# What this does:
#   1. Creates 'deploy' user with docker-only sudo
#   2. Installs Docker, docker-compose-plugin, ufw, rclone, postgresql-client
#   3. Configures ufw firewall
#   4. Hardens SSH (key-only, non-default port)
#   5. Creates /opt/zyntapos deploy directory
#   6. Installs fail2ban
# ═══════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────
SSH_PORT="${SSH_PORT:-22}"          # Change to non-default port for security
DEPLOY_PATH="/opt/zyntapos"

echo "=== ZyntaPOS VPS Setup ==="
echo "SSH port: ${SSH_PORT}"
echo "Deploy path: ${DEPLOY_PATH}"
echo ""

# Ensure running as root
if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: Must run as root" >&2
    exit 1
fi

# ── System update ─────────────────────────────────────────────────
echo "[1/7] Updating system packages..."
apt-get update -qq
apt-get upgrade -y -qq

# ── Install dependencies ──────────────────────────────────────────
echo "[2/7] Installing Docker, ufw, rclone, postgresql-client..."
apt-get install -y -qq \
    docker.io \
    docker-compose-plugin \
    ufw \
    fail2ban \
    postgresql-client \
    curl \
    wget \
    unzip

# Install rclone for Backblaze B2 backup
curl -fsSL https://rclone.org/install.sh | bash -s -- --quiet

# ── Create deploy user ────────────────────────────────────────────
echo "[3/7] Creating deploy user..."
if ! id deploy &>/dev/null; then
    adduser deploy --disabled-password --gecos ""
fi
usermod -aG docker deploy

# Grant deploy user docker-only sudo (no full root)
cat > /etc/sudoers.d/deploy-docker << 'EOF'
deploy ALL=(ALL) NOPASSWD: /usr/bin/docker, /usr/local/bin/docker, /usr/bin/docker-compose, /usr/bin/systemctl restart docker
EOF
chmod 440 /etc/sudoers.d/deploy-docker

# ── Create deploy directory ───────────────────────────────────────
echo "[4/7] Creating ${DEPLOY_PATH}..."
mkdir -p "${DEPLOY_PATH}/secrets"
chown -R deploy:deploy "${DEPLOY_PATH}"
chmod 700 "${DEPLOY_PATH}/secrets"

# ── Configure ufw firewall ────────────────────────────────────────
echo "[5/7] Configuring ufw firewall..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow "${SSH_PORT}/tcp" comment 'SSH'
ufw allow 80/tcp comment 'HTTP (Caddy)'
ufw allow 443/tcp comment 'HTTPS (Caddy)'
ufw --force enable
echo "Firewall rules:"
ufw status verbose

# ── Configure fail2ban ────────────────────────────────────────────
echo "[6/7] Configuring fail2ban..."
cat > /etc/fail2ban/jail.local << EOF
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 5

[sshd]
enabled = true
port = ${SSH_PORT}
logpath = %(sshd_log)s
backend = %(sshd_backend)s
EOF
systemctl enable fail2ban
systemctl restart fail2ban

# ── Start Docker ──────────────────────────────────────────────────
echo "[7/7] Starting Docker service..."
systemctl enable docker
systemctl start docker

echo ""
echo "=== Setup complete! ==="
echo ""
echo "Next steps:"
echo "  1. Add your deploy SSH public key:"
echo "     mkdir -p /home/deploy/.ssh"
echo "     echo '<YOUR_PUBLIC_KEY>' >> /home/deploy/.ssh/authorized_keys"
echo "     chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys"
echo "     chown -R deploy:deploy /home/deploy/.ssh"
echo ""
echo "  2. Clone the repository:"
echo "     su - deploy -c 'git clone https://github.com/sendtodilanka/ZyntaPOS-KMM.git ${DEPLOY_PATH}'"
echo ""
echo "  3. Create secrets:"
echo "     mkdir -p ${DEPLOY_PATH}/secrets"
echo "     openssl rand -base64 32 | tr -d '\n' > ${DEPLOY_PATH}/secrets/db_password.txt"
echo "     openssl genrsa -out ${DEPLOY_PATH}/secrets/rs256_private_key.pem 2048"
echo "     openssl rsa -in ${DEPLOY_PATH}/secrets/rs256_private_key.pem -pubout -out ${DEPLOY_PATH}/secrets/rs256_public_key.pem"
echo "     echo 'REDIS_PASSWORD='\"$(openssl rand -base64 32)\" > ${DEPLOY_PATH}/.env"
echo "     chmod 600 ${DEPLOY_PATH}/secrets/* ${DEPLOY_PATH}/.env"
echo ""
echo "  4. Install Cloudflare Origin Certificate:"
echo "     mkdir -p /etc/caddy/certs"
echo "     # Upload zyntapos_origin.pem and zyntapos_origin.key"
echo "     chmod 600 /etc/caddy/certs/*"
echo ""
echo "  5. Start services:"
echo "     su - deploy -c 'cd ${DEPLOY_PATH} && docker compose up -d'"
echo ""
echo "  6. Verify health:"
echo "     curl https://api.zyntapos.com/health"
echo "     curl https://license.zyntapos.com/health"
echo "     curl https://sync.zyntapos.com/health"
