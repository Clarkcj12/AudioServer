package club.imaginears.harmonia.paper.redis;

import club.imaginears.harmonia.core.message.RegionEvent;
import club.imaginears.harmonia.core.model.AudioState;
import club.imaginears.harmonia.core.redis.RedisKeys;
import club.imaginears.harmonia.paper.json.MessageJson;
import org.bukkit.Location;

import java.util.UUID;

public final class RedisPublisher {
    private static final long SESSION_TTL_SECONDS = 3600L;

    private final RedisService redis;
    private final String serverId;

    public RedisPublisher(RedisService redis, String serverId) {
        this.redis = redis;
        this.serverId = serverId;
    }

    public void publishRegionEvent(RegionEvent event) {
        redis.async().publish(RedisKeys.CHANNEL_EVENTS, MessageJson.serialize(event));
    }

    public void saveSession(AudioState state) {
        String key = RedisKeys.sessionKey(state.player());
        redis.async().set(key, MessageJson.serialize(state));
        redis.async().expire(key, SESSION_TTL_SECONDS);
    }

    public void deleteSession(UUID player) {
        redis.async().del(RedisKeys.sessionKey(player));
    }

    public void publishPosition(UUID player, String source, Location location) {
        redis.async().publish(RedisKeys.CHANNEL_POSITIONS, MessageJson.serializePosition(player, source, location));
    }

    public void trackPlayer(UUID player) {
        redis.async().sadd(RedisKeys.serverPlayersKey(serverId), player.toString());
    }

    public void untrackPlayer(UUID player) {
        redis.async().srem(RedisKeys.serverPlayersKey(serverId), player.toString());
    }
}
