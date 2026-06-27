#!/usr/bin/env bash
# One-time setup for the lightweight Campus Connect deploy on a fresh Ubuntu AWS box (1 GB free tier).
# Installs Docker, creates a 4 GB swap file (so the JVMs fit), then you bring up the stack.
#
#   bash deploy/setup-aws.sh
#
set -euo pipefail

echo "==> Installing Docker + git..."
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER" || true

echo "==> Creating a 4 GB swap file (lets a 1 GB box run the JVMs)..."
if ! sudo swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 4G /swapfile 2>/dev/null || sudo dd if=/dev/zero of=/swapfile bs=1M count=4096
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  echo 'vm.swappiness=40' | sudo tee /etc/sysctl.d/99-swap.conf >/dev/null
  sudo sysctl -p /etc/sysctl.d/99-swap.conf || true
  echo "    swap created."
else
  echo "    swap already present, skipping."
fi

echo ""
echo "==> Docker + swap ready. Next steps:"
echo "   1) cp .env.aws.example .env   then edit .env with your Atlas URI + JWT secret"
echo "   2) sudo docker compose -f docker-compose.aws.yml pull"
echo "   3) sudo docker compose -f docker-compose.aws.yml up -d"
echo "   4) watch it boot:  sudo docker compose -f docker-compose.aws.yml logs -f api-gateway"
echo ""
echo "   (first boot is SLOW on 1 GB + swap — give it a few minutes.)"
