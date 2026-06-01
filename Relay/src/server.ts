import express from 'express';
import { resolve } from 'node:path';
import { createServer as createHttpServer } from 'node:http';
import { Server } from 'socket.io';
import { Redis } from 'ioredis';
import { HarmoniaRelay } from './relay/HarmoniaRelay.js';
import { RedisSubscriber } from './redis/RedisSubscriber.js';
import { SessionStore } from './redis/SessionStore.js';

export function createServer() {
    const app = express();
    const http = createHttpServer(app);

    const io = new Server(http, {
        cors: {
            origin: process.env.CLIENT_URL ?? '*',
            methods: ['GET', 'POST'],
        },
        // Tokens are single-use, but Socket.IO auto-reconnects on any network blip and
        // re-presents the same (already-consumed) token. Recovery restores the session —
        // rooms and socket.data — WITHOUT re-running auth middleware, so a dropped packet
        // doesn't kill the audio session. Genuinely new connections still need a valid token.
        connectionStateRecovery: {},
    });

    const redisUrl = process.env.REDIS_URL ?? 'redis://localhost:6379';

    // Two connections by necessity: once `sub` enters subscribe mode it can't run other commands.
    // `kv` handles ordinary commands (session reads now; the commands publisher to Paper later).
    const sub = new Redis(redisUrl);
    const kv = new Redis(redisUrl);

    const relay = new HarmoniaRelay(io, new SessionStore(kv));
    new RedisSubscriber(sub, relay.clientNamespace).start().catch((err) => {
        console.error('[redis] subscriber failed to start', err);
    });

    app.use(express.json());
    app.get('/health', (_req, res) => res.json({ status: 'ok' }));

    // Audio assets: trackId is the filename (e.g. "castle-theme.ogg"), so the region flag
    // value maps straight to a file here. express.static normalizes the path and blocks
    // `..` traversal, sets Content-Type from the extension, and supports range requests.
    // Kept same-origin (via the client's dev proxy / prod gateway) so decodeAudioData needs no CORS.
    const audioDir = resolve(process.env.AUDIO_DIR ?? 'audio');
    app.use('/audio', express.static(audioDir));
    console.log(`[http] serving audio from ${audioDir}`);

    return http;
}
