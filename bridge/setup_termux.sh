#!/usr/bin/env bash
set -euo pipefail

# Idempotent Termux proot-distro Ubuntu 24.04 ARM64 setup script for TCG Studio MSE Bridge.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BRIDGE_DIR="${REPO_ROOT}/bridge"
BIN_DIR="${BRIDGE_DIR}/bin"
WORKSPACE_DIR="${BRIDGE_DIR}/workspace"
VENV_DIR="${REPO_ROOT}/.venv"
MODERN_BIN="${BIN_DIR}/magicseteditor-modern"
MODERN_SRC_DIR="${WORKSPACE_DIR}/src/mse-modern"

echo "==> [1/4] Installing Ubuntu ARM64 build, Xvfb, fontconfig, and Python packages..."
export DEBIAN_FRONTEND=noninteractive
APT_CMD="apt-get"
if [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; then
  APT_CMD="sudo apt-get"
fi

${APT_CMD} update
${APT_CMD} install -y --no-install-recommends \
  git \
  curl \
  unzip \
  cmake \
  g++ \
  pkg-config \
  xvfb \
  fontconfig \
  python3 \
  python3-venv \
  python3-pip \
  libwxgtk3.2-dev \
  libboost-all-dev \
  libhunspell-dev

echo "==> [2/4] Setting up Python virtual environment and bridge dependencies..."
mkdir -p "${BIN_DIR}" "${WORKSPACE_DIR}/upstream" "${WORKSPACE_DIR}/custom_templates"
if [ ! -d "${VENV_DIR}" ]; then
  python3 -m venv "${VENV_DIR}"
fi
"${VENV_DIR}/bin/pip" install --upgrade pip
"${VENV_DIR}/bin/pip" install -r "${BRIDGE_DIR}/requirements.txt"

echo "==> [3/4] Checking for ARM64 Magic Set Editor binaries..."
if [ -x "${BIN_DIR}/magicseteditor-modern-arm64" ] && [ ! -x "${MODERN_BIN}" ]; then
  ln -sf "${BIN_DIR}/magicseteditor-modern-arm64" "${MODERN_BIN}"
fi
if [ -x "${BIN_DIR}/magicseteditor-legacy-arm64" ] && [ ! -x "${BIN_DIR}/magicseteditor-legacy" ]; then
  ln -sf "${BIN_DIR}/magicseteditor-legacy-arm64" "${BIN_DIR}/magicseteditor-legacy"
fi

if [ ! -x "${MODERN_BIN}" ]; then
  echo "    Prebuilt modern ARM64 binary not found in ${BIN_DIR}."
  echo "    Cloning and compiling G-e-n-e-v-e-n-s-i-S/MagicSetEditor2 via CMake..."
  mkdir -p "$(dirname "${MODERN_SRC_DIR}")"
  if [ ! -d "${MODERN_SRC_DIR}/.git" ]; then
    git clone --depth 1 https://github.com/G-e-n-e-v-e-n-s-i-S/MagicSetEditor2.git "${MODERN_SRC_DIR}"
  else
    git -C "${MODERN_SRC_DIR}" pull --ff-only || true
  fi
  cmake -S "${MODERN_SRC_DIR}" -B "${MODERN_SRC_DIR}/build" -DCMAKE_BUILD_TYPE=Release
  cmake --build "${MODERN_SRC_DIR}/build" -j"$(nproc)"
  BUILT_BIN="$(find "${MODERN_SRC_DIR}/build" -maxdepth 4 -type f -executable -name 'magicseteditor' | head -n 1)"
  if [ -z "${BUILT_BIN}" ]; then
    echo "ERROR: CMake finished but magicseteditor executable was not found." >&2
    exit 1
  fi
  cp "${BUILT_BIN}" "${MODERN_BIN}"
  chmod +x "${MODERN_BIN}"
  echo "    Installed modern MSE ARM64 binary to ${MODERN_BIN}."
else
  echo "    Found existing modern MSE binary at ${MODERN_BIN}."
fi

echo "==> [4/4] Running delta-only upstream pack & font sync (Full-Magic-Pack + Full-Non-Magic-Pack)..."
cd "${REPO_ROOT}"
"${VENV_DIR}/bin/python" -m bridge.sync_upstream

echo ""
echo "Setup complete! Start the local MSE render engine with:"
echo "  cd ${REPO_ROOT} && bash bridge/start_bridge.sh"
