import type { Redis } from 'ioredis';
import type { Namespace } from 'socket.io';
import type { RegionEvent, PositionUpdate } from '../relay/types.js';

// Channel names mirror club.imaginears.harmonia.core.redis.RedisKeys (Java) and PROTOCOL.md.
const CHANNEL_EVENTS = 'harmonia:events';
const CHANNEL_POSITIONS = 'harmonia:positions';

/**
 * Subscribes to the Paper → Relay Redis channels and forwards each message into the
 * matching `player:{uuid}` Socket.IO room.
 *
 * v1 is a pure relay: region events and positions are forwarded verbatim. Mapping a
 * region to a track (the per-region audio config) is out of scope — see PROTOCOL.md.
 *
 * The injected connection is dedicated to subscribing: once subscribed, ioredis cannot
 * issue ordinary commands on it. Publishing back to Paper (harmonia:commands:{serverId})
 * must use a separate connection.
 */
export class RedisSubscriber {
    constructor(
        private readonly sub: Redis,
        private readonly clients: Namespace,
    ) {}

    async start(): Promise<void> {
        this.sub.on('message', (channel, message) => this.dispatch(channel, message));
        await this.sub.subscribe(CHANNEL_EVENTS, CHANNEL_POSITIONS);
        console.log(`[redis] subscribed to ${CHANNEL_EVENTS}, ${CHANNEL_POSITIONS}`);
    }

    private dispatch(channel: string, message: string): void {
        let parsed: unknown;
        try {
            parsed = JSON.parse(message);
        } catch {
            console.warn(`[redis] non-JSON message on ${channel}`);
            return;
        }

        switch (channel) {
            case CHANNEL_EVENTS: {
                const event = asRegionEvent(parsed);
                if (!event) {
                    console.warn(`[redis] malformed region_event on ${channel}`);
                    return;
                }
                this.clients.to(`player:${event.player}`).emit('region_event', event);
                return;
            }
            case CHANNEL_POSITIONS: {
                const pos = asPosition(parsed);
                // Positions arrive at 5 Hz/player — swallow malformed ones silently to avoid log spam.
                if (!pos) return;
                this.clients.to(`player:${pos.player}`).emit('position', pos);
                return;
            }
        }
    }
}

// ── message guards (these cross a process boundary, so validate defensively) ──────────

function isString(v: unknown): v is string {
    return typeof v === 'string';
}

function isFiniteNumber(v: unknown): v is number {
    return typeof v === 'number' && Number.isFinite(v);
}

function asRegionEvent(v: unknown): RegionEvent | null {
    if (typeof v !== 'object' || v === null) return null;
    const o = v as Record<string, unknown>;
    if (!isString(o.player) || !isString(o.source) || !isString(o.regionId)) return null;
    if (o.type !== 'ENTER' && o.type !== 'EXIT') return null;
    const trackId = o.trackId === undefined ? null : o.trackId;
    if (trackId !== null && !isString(trackId)) return null;
    return { player: o.player, source: o.source, regionId: o.regionId, type: o.type, trackId };
}

function asPosition(v: unknown): PositionUpdate | null {
    if (typeof v !== 'object' || v === null) return null;
    const o = v as Record<string, unknown>;
    if (!isString(o.player) || !isString(o.source) || !isString(o.world)) return null;
    if (!isFiniteNumber(o.x) || !isFiniteNumber(o.y) || !isFiniteNumber(o.z)) return null;
    if (!isFiniteNumber(o.yaw) || !isFiniteNumber(o.pitch)) return null;
    return {
        player: o.player, source: o.source, world: o.world,
        x: o.x, y: o.y, z: o.z, yaw: o.yaw, pitch: o.pitch,
    };
}
