# Audio assets

Files here are served by the relay at `/audio/{filename}`.

A WorldGuard region's `harmonia-audio` flag value **is** the filename, so to make a region
play a track:

1. Drop the file here, e.g. `castle-theme.ogg`.
2. In-game: `/rg flag <region> harmonia-audio castle-theme.ogg`

Use a browser-decodable format (`.ogg`, `.mp3`, `.wav`, `.m4a`/AAC, `.opus`). The client loads
the whole file via `decodeAudioData`, so prefer short, loopable clips for ambient regions —
long tracks are a known v1 limitation (no streaming yet).

Audio binaries are git-ignored (see root `.gitignore`); this directory and its docs are tracked
via `.gitkeep`. Override the location with the `AUDIO_DIR` env var.
