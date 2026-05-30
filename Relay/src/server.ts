import express from 'express';
import { createServer as createHttpServer } from 'node:http';
import { Server } from 'socket.io';
import { Redis } from 'ioredis';
import { HarmoniaRelay } from './relay/HarmoniaRelay.js';
import { RedisSubscriber } from './redis/RedisSubscriber.js';

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

    const relay = new HarmoniaRelay(io);

    // Dedicated subscriber connection — once subscribed, ioredis can't run other commands on it.
    // The future commands publisher (harmonia:commands:{serverId} → Paper) must use its OWN connection.
    const redisUrl = process.env.REDIS_URL ?? 'redis://localhost:6379';
    const sub = new Redis(redisUrl);
    new RedisSubscriber(sub, relay.clientNamespace).start().catch((err) => {
        console.error('[redis] subscriber failed to start', err);
    });

    app.use(express.json());
    app.get('/health', (_req, res) => res.json({ status: 'ok' }));

    return http;
}
