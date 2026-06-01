# Local integration harness

Run and hear the relay → client → audio path **without any Minecraft servers**. A dev script
fakes the Velocity + Paper plugins by registering a token over the real `/plugin` protocol and
seeding the audio state Paper would normally persist.

## Prerequisites

- Docker + Docker Compose
- Node.js 22+

## Hear it (browser)

From the repo root:

```bash
# 1. Start Redis + relay (relay also serves /audio). Leave running.
docker compose up --build

# 2. Start the web client (vite dev server on :5173).
cd Client && npm install && npm run dev

# 3. Mint a token + seed a playing track. Prints a URL; auto-generates a test tone.
cd Relay && npm install && npm run dev:simulate
```

Open the printed `http://localhost:5173/?token=…` URL and click **Enable audio**. You should hear
a looping 440 Hz tone — that's the connect-time replay: the relay read the seeded `AudioState`,
emitted a `PLAY` command, and the client fetched `/audio/test-tone.wav` and played it.

The token is single-use with a ~60 s TTL: re-run `npm run dev:simulate` for a fresh one, and note
that **refreshing the page fails** (`invalid token`) by design — reopen with a new token.

To use your own file: drop it in `Relay/audio/` and pass the filename, e.g.
`npm run dev:simulate ambient.ogg`.

## Verify it (headless, no browser)

```bash
docker compose up --build      # in one terminal
cd Relay && npm run dev:verify  # in another
```

`dev:verify` drives **real sockets** through the whole relay path — registers a token via
`/plugin`, connects to `/client`, and asserts it receives `session_ready` followed by an
`audio_command(PLAY, test-tone.wav)` in that order. Exits `0` on success, `1` on failure.

## What this proves — and what it doesn't

**Proves** (over real sockets): `/plugin` secret auth, `session_link` token registration,
single-use token consume on `/client`, the Redis session read, the connect-time replay, and the
emit-before-join ordering — plus static `/audio` serving.

**Does NOT prove:** the Paper side. The harness seeds Redis directly, so the WorldGuard
`harmonia-audio` flag, its `onLoad()` registration timing, and `RegionListener` reading it are
still unexercised — those need a real Paper + WorldGuard server. `dev:verify` also stops at the
socket layer; the browser Web Audio decode/playback is only covered by the "Hear it" path above.

## Faster iteration (relay on host)

Rebuilding the relay image on every change is slow. To iterate on relay code, run only Redis in
Docker and the relay on the host:

```bash
docker compose up redis
cd Relay && npm run dev          # tsx watch, picks up REDIS_URL=redis://localhost:6379 by default
```

## Config

The dev scripts default to `PLUGIN_SECRET=dev-secret` (matching `docker-compose.yml`),
`RELAY_URL=http://localhost:3000`, `REDIS_URL=redis://localhost:6379`, and
`CLIENT_URL=http://localhost:5173`. Override any via environment variables.
