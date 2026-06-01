import { randomUUID } from 'node:crypto';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { Redis } from 'ioredis';
import { io } from 'socket.io-client';
import {
    RELAY_URL, REDIS_URL, PLUGIN_SECRET, CLIENT_URL, DEFAULT_TRACK,
    generateToneWav, seedSession,
} from './lib.js';

/**
 * Fakes the Velocity + Paper plugins so you can hear audio without a Minecraft server:
 *   1. seeds an AudioState in Redis (so the relay replays PLAY on connect),
 *   2. registers a single-use token via the real /plugin protocol,
 *   3. prints the client URL.
 *
 * Usage: npm run dev:simulate [trackId]    (default: test-tone.wav, auto-generated if missing)
 */
const trackId = process.argv[2] ?? DEFAULT_TRACK;
const audioDir = resolve(process.env.AUDIO_DIR ?? 'audio');
const audioPath = resolve(audioDir, trackId);

if (!existsSync(audioPath) && trackId === DEFAULT_TRACK) {
    generateToneWav(audioPath);
    console.log(`Generated test tone at ${audioPath}`);
}
if (!existsSync(audioPath)) {
    console.error(`Audio file not found: ${audioPath}`);
    console.error('Put a decodable file there, or omit the argument to use the generated tone.');
    process.exit(1);
}

// One UUID, used for BOTH the session_link player field and the seeded session key.
const player = randomUUID();
const token = randomUUID();
const redis = new Redis(REDIS_URL);

const plugin = io(`${RELAY_URL}/plugin`, { auth: { secret: PLUGIN_SECRET } });

plugin.on('connect_error', (err) => {
    console.error(`Failed to connect to relay /plugin: ${err.message}`);
    console.error('Is the relay running, and does PLUGIN_SECRET match?');
    process.exit(1);
});

plugin.on('connect', async () => {
    await seedSession(redis, player, trackId);   // seed BEFORE registering the token
    plugin.emit('session_link', { player, token });

    console.log('\n=== Harmonia dev simulation ===');
    console.log(`  player : ${player}`);
    console.log(`  track  : ${trackId}`);
    console.log('\nOpen the client and click "Enable audio":');
    console.log(`  ${CLIENT_URL}/?token=${token}`);
    console.log('\n(token is valid ~60s — re-run for a fresh one. Ctrl-C to stop.)\n');
});
