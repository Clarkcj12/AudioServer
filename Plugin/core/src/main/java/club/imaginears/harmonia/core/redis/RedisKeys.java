package club.imaginears.harmonia.core.redis;

import java.util.UUID;

/** Central registry of every Redis key and channel name used by Harmonia. */
public final class RedisKeys {

    // ── pub/sub channels ─────────────────────────────────────────────────────

    /** Paper publishes region/audio events here; the Relay subscribes. */
    public static final String CHANNEL_EVENTS = "harmonia:events";

    /** Paper publishes player position updates here at 5 Hz; kept separate from CHANNEL_EVENTS to avoid mixing high- and low-frequency traffic. */
    public static final String CHANNEL_POSITIONS = "harmonia:positions";

    /** The Relay publishes commands here; Paper subscribes per-server. */
    public static String channelCommands(String serverId) {
        return "harmonia:commands:" + serverId;
    }

    // ── key-value ─────────────────────────────────────────────────────────────

    /** Per-player audio state, serialised as JSON. TTL should match session timeout. */
    public static String sessionKey(UUID player) {
        return "harmonia:session:" + player;
    }

    /** Set of currently connected player UUIDs on a given server. */
    public static String serverPlayersKey(String serverId) {
        return "harmonia:players:" + serverId;
    }

    private RedisKeys() {}
}
