# Accessibility Plus Speech Bridge

This folder contains a small **local speech bridge** service that runs outside RuneLite.

The RuneLite plugin sends text to the bridge over `localhost`, the bridge uses **Piper** to synthesize speech, and the bridge synthesizes speech with Piper and plays it locally (RuneLite does not handle audio).

This mirrors the architecture used by established RuneLite TTS plugins: the heavy TTS engine runs outside the client.

## Requirements

1. Install Piper (download a release for your OS).
2. Download at least one Piper voice model (`.onnx`). Some voices also include a matching `.json` config.

## Run the bridge

Windows example:

```bat
python piper_bridge_server.py --port 59125 --piper "C:\piper\piper.exe" --model "C:\piper\voices\en_US-amy-low.onnx" --config "C:\piper\voices\en_US-amy-low.onnx.json"
```

macOS/Linux example:

```bash
python3 piper_bridge_server.py --port 59125 --piper /usr/local/bin/piper --model /path/to/voice.onnx --config /path/to/voice.onnx.json
```

If your voice does not require a config file, omit `--config`.

## Configure RuneLite

RuneLite → Plugins → Accessibility Plus → Speech

* Enable TTS: **on**
* TTS backend: **BRIDGE**
* Bridge URL: `http://127.0.0.1:59125/speak`

## Health check

Open this in a browser while the server is running:

* `http://127.0.0.1:59125/health`

You should see `OK`.
