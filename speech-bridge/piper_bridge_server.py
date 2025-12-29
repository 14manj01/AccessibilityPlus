#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""Local speech bridge for Accessibility Plus (Piper).

This service runs OUTSIDE RuneLite. The RuneLite plugin should only POST text
to this bridge.

Endpoints
- POST /speak
    JSON body: {"text":"..."}
    Returns: 200 OK (text/plain)
    The bridge synthesizes speech with Piper and plays it on the local machine.

- GET /health
    Returns: OK
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Optional


def _which(cmd: str) -> Optional[str]:
    from shutil import which
    return which(cmd)


def play_wav(path: str) -> None:
    """Play a wav file using an OS-available player."""
    system = platform.system().lower()

    # Windows: use PowerShell SoundPlayer
    if system.startswith("win"):
        ps = _which("powershell") or _which("pwsh")
        if ps:
            script = f'(New-Object System.Media.SoundPlayer "{path}").PlaySync();'
            subprocess.run([ps, "-NoProfile", "-Command", script], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return

    # macOS: afplay
    if system == "darwin":
        afplay = _which("afplay")
        if afplay:
            subprocess.run([afplay, path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return

    # Linux: try aplay then paplay
    for player in ("aplay", "paplay"):
        p = _which(player)
        if p:
            subprocess.run([p, path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return

    raise RuntimeError("No audio player found (need afplay/aplay/paplay or PowerShell).")


class PiperBridge:
    def __init__(self, piper: str, model: str, config: Optional[str] = None, extra_args: Optional[list[str]] = None):
        self.piper = piper
        self.model = model
        self.config = config
        self.extra_args = extra_args or []

    def synth_to_wav(self, text: str, wav_path: str) -> None:
        cmd = [self.piper, "--model", self.model, "--output_file", wav_path]
        if self.config:
            cmd += ["--config", self.config]
        cmd += self.extra_args

        proc = subprocess.run(
            cmd,
            input=text.encode("utf-8"),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if proc.returncode != 0:
            raise RuntimeError(proc.stderr.decode("utf-8", errors="replace").strip() or "Piper failed")


class Handler(BaseHTTPRequestHandler):
    server_version = "APSpeechBridge/1.1"

    def _send_text(self, code: int, body: str):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self._send_text(200, "OK")
            return
        self._send_text(404, "Not found")

    def do_POST(self):
        if self.path != "/speak":
            self._send_text(404, "Not found")
            return

        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0:
            self._send_text(400, "Missing body")
            return

        try:
            raw = self.rfile.read(length)
            payload = json.loads(raw.decode("utf-8", errors="replace"))
            text = str(payload.get("text", "")).strip()
        except Exception:
            self._send_text(400, "Invalid JSON")
            return

        if not text:
            self._send_text(200, "OK")
            return

        bridge: PiperBridge = self.server.bridge  # type: ignore[attr-defined]

        def worker():
            try:
                with tempfile.TemporaryDirectory(prefix="ap_speech_") as td:
                    wav_path = os.path.join(td, "speech.wav")
                    bridge.synth_to_wav(text, wav_path)
                    play_wav(wav_path)
            except Exception:
                pass

        threading.Thread(target=worker, daemon=True).start()
        self._send_text(200, "OK")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=59125)
    ap.add_argument("--piper", required=True, help="Path to piper executable")
    ap.add_argument("--model", required=True, help="Path to Piper .onnx model")
    ap.add_argument("--config", default=None, help="Optional Piper config .json")
    ap.add_argument("--extra", nargs="*", default=[], help="Extra args passed to Piper")
    args = ap.parse_args()

    piper = str(Path(args.piper).expanduser())
    model = str(Path(args.model).expanduser())
    cfg = str(Path(args.config).expanduser()) if args.config else None

    if not Path(piper).exists():
        raise SystemExit(f"piper not found: {piper}")
    if not Path(model).exists():
        raise SystemExit(f"model not found: {model}")
    if cfg and not Path(cfg).exists():
        raise SystemExit(f"config not found: {cfg}")

    httpd = HTTPServer((args.host, args.port), Handler)
    httpd.bridge = PiperBridge(piper=piper, model=model, config=cfg, extra_args=args.extra)  # type: ignore[attr-defined]
    print(f"AP Speech Bridge running on http://{args.host}:{args.port}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
