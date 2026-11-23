#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VENV_DIR="${REPO_ROOT}/.venv"
HOST="${MSE_BRIDGE_HOST:-127.0.0.1}"
PORT="${MSE_BRIDGE_PORT:-8765}"

if [ ! -d "${VENV_DIR}" ]; then
  echo "ERROR: Python virtual environment not found at ${VENV_DIR}." >&2
  echo "Run 'bash bridge/setup_termux.sh' first." >&2
  exit 1
fi

if ! command -v xvfb-run >/dev/null 2>&1; then
  echo "WARNING: xvfb-run is not installed. Run 'bash bridge/setup_termux.sh' to install xvfb." >&2
fi

if command -v fc-cache >/dev/null 2>&1; then
  mkdir -p "${HOME}/.local/share/fonts/mse-studio"
  fc-cache -f "${HOME}/.local/share/fonts/mse-studio" >/dev/null 2>&1 || true
fi

# shellcheck disable=SC1091
source "${VENV_DIR}/bin/activate"
cd "${REPO_ROOT}"

echo "Starting TCG Studio MSE Bridge on http://${HOST}:${PORT} ..."
exec uvicorn bridge.server:app --host "${HOST}" --port "${PORT}"
