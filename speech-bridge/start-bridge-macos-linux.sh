#!/usr/bin/env bash
set -euo pipefail

# Accessibility Plus speech bridge starter (macOS/Linux)
# 1) Copy config.example.env -> config.env
# 2) Edit config.env and set PIPER_PATH, MODEL_PATH, CONFIG_PATH

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CFG="${SCRIPT_DIR}/config.env"

if [[ ! -f "${CFG}" ]]; then
  echo "[ERROR] config.env not found."
  echo "Copy speech-bridge/config.example.env to speech-bridge/config.env and edit paths."
  exit 1
fi

# shellcheck disable=SC1090
source "${CFG}"

PORT="${PORT:-59125}"

echo "Starting Accessibility Plus Piper bridge on port ${PORT}"
python3 "${SCRIPT_DIR}/piper_bridge_server.py" --port "${PORT}" --piper "${PIPER_PATH}" --model "${MODEL_PATH}" --config "${CONFIG_PATH}"
