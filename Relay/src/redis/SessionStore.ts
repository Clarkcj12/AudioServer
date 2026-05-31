import type { Redis } from 'ioredis';
import type { AudioState } from '../relay/types.js';

// Mirrors club.imaginears.harmonia.core.redis.RedisKeys.sessionKey(uuid) (Java):
// "harmonia:session:" + uuid.toString() (lowercase, hyphenated).
const sessionKey = (player: string): string => `harmonia:session:${player}`;

/**
 * Reads the per-player {@link AudioState} snapshot that Paper persists in Redis.
 * Used to replay the current track when a client (re)connects, so a player who is
 * already standing in an audio region hears it without waiting to cross a boundary.
 *
 * Uses a general Redis connection — must NOT be the one held in subscribe mode.
 */
export class SessionStore {
    constructor(private readonly redis: Redis) {}

    async current(player: string): Promise<AudioState | null> {
        const raw = await this.redis.get(sessionKey(player));
        if (!raw) return null;
        try {
            const parsed = JSON.parse(raw) as unknown;
            return typeof parsed === 'object' && parsed !== null ? (parsed as AudioState) : null;
        } catch {
            return null;
        }
    }
}
