/** TTL for a pending handoff token. Mirrors the 60 s contract in PROTOCOL.md. */
const TOKEN_TTL_MS = 60_000;

interface PendingToken {
    player: string;          // UUID
    timer: NodeJS.Timeout;
}

/**
 * Tracks single-use handoff tokens that Velocity issues to players.
 *
 * Velocity generates the token (a UUID) and emits `session_link`; the relay only
 * stores, validates, and consumes it. Single-use is enforced here, not by Velocity:
 * `consume` returns the player UUID exactly once, then forgets the token.
 *
 * In-memory by design — tokens live at most 60 s, so a relay restart simply forces
 * affected players to reload (a fresh token is issued on their next session_link).
 */
export class TokenService {
    private readonly byToken = new Map<string, PendingToken>();
    private readonly byPlayer = new Map<string, string>();   // player UUID → token

    /** Stores a Velocity-issued token, replacing any prior pending token for the same player. */
    register(token: string, player: string): void {
        this.revoke(player);   // a re-join supersedes any earlier pending token

        const timer = setTimeout(() => {
            this.byToken.delete(token);
            if (this.byPlayer.get(player) === token) this.byPlayer.delete(player);
        }, TOKEN_TTL_MS);
        timer.unref();   // don't keep the process alive for a pending token

        this.byToken.set(token, { player, timer });
        this.byPlayer.set(player, token);
    }

    /**
     * Validates and consumes a token. Returns the player UUID on first use, then `null`
     * for that token forever after (single-use). Returns `null` for unknown/expired tokens.
     */
    consume(token: string): string | null {
        const entry = this.byToken.get(token);
        if (!entry) return null;

        clearTimeout(entry.timer);
        this.byToken.delete(token);
        if (this.byPlayer.get(entry.player) === token) this.byPlayer.delete(entry.player);
        return entry.player;
    }

    /** Invalidates any pending token for a player — on `session_unlink` or disconnect. */
    revoke(player: string): void {
        const token = this.byPlayer.get(player);
        if (token === undefined) return;

        const entry = this.byToken.get(token);
        if (entry) clearTimeout(entry.timer);
        this.byToken.delete(token);
        this.byPlayer.delete(player);
    }
}
