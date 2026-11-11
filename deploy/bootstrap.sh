#!/usr/bin/env bash
# Prepares a fresh Ubuntu server for the stack. Run once, as root, from a clone
# of this repository on the server:
#
#   git clone https://github.com/antoniosifov/popcorn-palace /opt/popcorn-palace
#   /opt/popcorn-palace/deploy/bootstrap.sh
#
# Safe to run again: every step looks at what is already there.
# What it does is listed in deploy/README.md.
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Run as root." >&2
  exit 1
fi

repo="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
user=deploy

echo "== Docker"
if ! command -v docker > /dev/null; then
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  # shellcheck disable=SC1091
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

# Container logs are kept to 30 MB each instead of growing until the disk is full.
if [[ ! -f /etc/docker/daemon.json ]]; then
  cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
  systemctl restart docker
fi

echo "== Swap"
# Room to breathe on a server with 4 GB: without it, a moment of pressure ends
# with the kernel killing a container.
if [[ -z "$(swapon --noheadings --show)" ]]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile > /dev/null
  swapon /swapfile
  echo "/swapfile none swap sw 0 0" >> /etc/fstab
fi

echo "== Firewall"
# SSH and the web server; nothing else is listening anyway.
apt-get install -y -qq ufw
ufw allow OpenSSH > /dev/null
ufw allow 80/tcp > /dev/null
ufw allow 443/tcp > /dev/null
ufw allow 443/udp > /dev/null
ufw --force enable > /dev/null

echo "== The account releases are made from"
# No password and no sudo. Membership of the docker group is what lets it
# run the stack (and is, on this machine, as good as root: keep its key safe).
if ! id "$user" > /dev/null 2>&1; then
  useradd --create-home --shell /bin/bash --groups docker "$user"
fi
install -d -m 700 -o "$user" -g "$user" "/home/$user/.ssh"
chown -R "$user:$user" "$repo"

echo "== Settings"
env_file="$repo/.env"
if [[ ! -f "$env_file" ]]; then
  cp "$repo/deploy/.env.example" "$env_file"
  for name in DB_PASSWORD JWT_SECRET ADMIN_PASSWORD GRAFANA_PASSWORD; do
    sed -i "s|^$name=\$|$name=$(openssl rand -hex 24)|" "$env_file"
  done
  echo "Created $env_file with new secrets."
else
  echo "$env_file exists, left as it is."
fi
chown "$user:$user" "$env_file"
chmod 600 "$env_file"

cat <<TEXT

Done. What is left, by hand:

1. Fill in $env_file: the four *_HOST names, ADMIN_EMAIL, MAIL_PASSWORD,
   MAIL_FROM, and TMDB_API_KEY if the programme should be the films in cinemas now.
2. Let the Deploy workflow in: put the public half of its key into
   /home/$user/.ssh/authorized_keys, on one line, limited to releasing:

   command="$repo/deploy/deploy.sh",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty ssh-ed25519 AAAA... deploy

3. Make the first release: sudo -u $user $repo/deploy/deploy.sh
TEXT
