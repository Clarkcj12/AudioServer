import type { Server, Namespace, Socket } from 'socket.io';
import { TokenService } from '../auth/TokenService.js';
import type { SessionStore } from '../redis/SessionStore.js';
import type { AudioCommand, AudioState, RegionEvent, SessionLink, SessionUnlink } from './types.js';

/**
 * Wires up the two Socket.IO namespaces:
 *   /plugin  — Velocity plugin connections (shared-secret auth)
 *   /client  — browser web client connections (single-use token auth)
 */
export class HarmoniaRelay {
    private readonly plugins: Namespace;
    private readonly clients: Namespace;
    private readonly tokens: TokenService;
    private readonly sessions: SessionStore;

    constructor(io: Server, sessions: SessionStore, tokens: TokenService = new TokenService()) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.plugins = io.of('/plugin');
        this.clients = io.of('/client');
        this.registerPluginHandlers();
        this.registerClientHandlers();
    }

    /** The browser-facing namespace, exposed so the Redis subscriber can forward into player rooms. */
    get clientNamespace(): Namespace {
        return this.clients;
    }

    private registerPluginHandlers(): void {
        const expectedSecret = process.env.PLUGIN_SECRET;

        // Reject every plugin connection unless it presents the shared secret.
        // Fail closed: if PLUGIN_SECRET is unset, no plugin may connect.
        this.plugins.use((socket, next) => {
            const secret = socket.handshake.auth.secret as string | undefined;
            if (!expectedSecret || secret !== expectedSecret) {
                next(new Error('unauthorized'));
                return;
            }
            next();
        });

        this.plugins.on('connection', (socket: Socket) => {
            console.log(`[plugin] connected  id=${socket.id}`);

            socket.on('session_link', (payload: SessionLink) => {
                if (typeof payload?.player !== 'string' || typeof payload?.token !== 'string') {
                    console.warn(`[plugin] malformed session_link from id=${socket.id}`);
                    return;
                }
                this.tokens.register(payload.token, payload.player);
                console.log(`[plugin] session_link  player=${payload.player}`);
            });

            socket.on('session_unlink', (payload: SessionUnlink) => {
                if (typeof payload?.player !== 'string') {
                    console.warn(`[plugin] malformed session_unlink from id=${socket.id}`);
                    return;
                }
                this.tokens.revoke(payload.player);
                console.log(`[plugin] session_unlink  player=${payload.player}`);
            });

            socket.on('audio_command', (cmd: AudioCommand) => {
                this.clients.to(`player:${cmd.player}`).emit('audio_command', cmd);
            });

            socket.on('region_event', (event: RegionEvent) => {
                this.clients.to(`player:${event.player}`).emit('region_event', event);
            });

            socket.on('disconnect', (reason) => {
                console.log(`[plugin] disconnected  id=${socket.id}  reason=${reason}`);
            });
        });
    }

    private registerClientHandlers(): void {
        // Validate (and consume) the handoff token before the connection is accepted.
        // The client never asserts its own player UUID — it's derived from the token.
        this.clients.use((socket, next) => {
            const token = socket.handshake.auth.token as string | undefined;
            if (!token) {
                next(new Error('missing token'));
                return;
            }
            const player = this.tokens.consume(token);
            if (!player) {
                next(new Error('invalid token'));
                return;
            }
            socket.data.player = player;
            next();
        });

        this.clients.on('connection', (socket: Socket) => {
            const playerId = socket.data.player as string;
            void this.onClientConnected(socket, playerId);

            socket.on('disconnect', (reason) => {
                console.log(`[client] disconnected  player=${playerId}  reason=${reason}`);
            });
        });
    }

    /**
     * Read current state → emit (session_ready + any replay) → join the room, in that order.
     * Joining last guarantees a live region event can't broadcast in ahead of the replay and
     * leave the client on a stale track. Direct socket.emit works regardless of room membership.
     */
    private async onClientConnected(socket: Socket, playerId: string): Promise<void> {
        console.log(`[client] connected  player=${playerId}  id=${socket.id}`);

        let state: AudioState | null = null;
        try {
            state = await this.sessions.current(playerId);
        } catch (err) {
            console.error(`[client] failed to read session for ${playerId}`, err);
        }

        socket.emit('session_ready', { player: playerId });

        if (shouldReplay(state)) {
            const cmd: AudioCommand = {
                player: playerId,
                source: 'relay',
                trackId: state.trackId,
                action: 'PLAY',
                volume: state.volume,
            };
            socket.emit('audio_command', cmd);
            console.log(`[client] replayed track=${state.trackId} player=${playerId}`);
        }

        socket.join(`player:${playerId}`);
    }
}

/** Replay only an actively-playing track. Mirrors AudioState.isPlaying() on the Java side. */
function shouldReplay(state: AudioState | null): state is AudioState & { trackId: string } {
    return state !== null
        && state.trackId !== null
        && (state.action === 'PLAY' || state.action === 'RESUME');
}
