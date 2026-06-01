import { randomUUID } from 'node:crypto';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { Redis } from 'ioredis';
import { io, type Socket } from 'socket.io-client';
import { RELAY_URL, REDIS_URL, PLUGIN_SECRET, DEFAULT_TRACK, generateToneWav, seedSession } from './lib.js';

/**
 * Headless end-to-end check of the relay path over REAL sockets (no browser):
 *   seed session → register token via /plugin → connect /client → assert it receives
 *   session_ready, then an audio_command(PLAY, trackId) in that order.
 *
 * Proves: /plugin auth, session_link, token consume on /client, KV session read, the
 * connect-time replay, and emit-before-join ordering. Exits 0 on success, 1 on failure.
 * Does NOT exercise the Paper side (we seed Redis directly) or the browser Web Audio render.
 */
const trackId = DEFAULT_TRACK;
const audioPath = resolve(process.env.AUDIO_DIR ?? 'audio', trackId);
if (!existsSync(audioPath)) generateToneWav(audioPath);

const player = randomUUID();
const token = randomUUID();
const redis = new Redis(REDIS_URL);

let plugin: Socket;
let client: Socket;
let gotReady = false;

function done(code: number, msg: string): void {
    console.log(msg);
    plugin?.close();
    client?.close();
    void redis.quit().finally(() => process.exit(code));
}
const fail = (msg: string) => done(1, `FAIL: ${msg}`);

const timer = setTimeout(() => fail('timed out after 8s waiting for events'), 8000);

plugin = io(`${RELAY_URL}/plugin`, { auth: { secret: PLUGIN_SECRET } });
plugin.on('connect_error', (e) => fail(`/plugin connect: ${e.message}`));

plugin.on('connect', async () => {
    await seedSession(redis, player, trackId);
    plugin.emit('session_link', { player, token });
    setTimeout(connectClient, 250);   // let the relay register the token first
});

function connectClient(): void {
    client = io(`${RELAY_URL}/client`, { auth: { token } });
    client.on('connect_error', (e) => fail(`/client connect: ${e.message}`));

    client.on('session_ready', (p: { player: string }) => {
        if (p.player !== player) return fail(`session_ready player ${p.player} != ${player}`);
        gotReady = true;
    });

    client.on('audio_command', (cmd: { trackId: string; action: string }) => {
        clearTimeout(timer);
        if (!gotReady) return fail('audio_command arrived before session_ready');
        if (cmd.action !== 'PLAY') return fail(`audio_command action ${cmd.action} != PLAY`);
        if (cmd.trackId !== trackId) return fail(`audio_command trackId ${cmd.trackId} != ${trackId}`);
        done(0, `PASS: session_ready then audio_command(PLAY ${cmd.trackId}) received over real sockets`);
    });
}
