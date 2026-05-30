package club.imaginears.harmonia.paper.redis;

import club.imaginears.harmonia.core.message.AudioAction;
import club.imaginears.harmonia.core.message.AudioCommand;
import club.imaginears.harmonia.core.model.AudioState;
import club.imaginears.harmonia.core.redis.RedisKeys;
import club.imaginears.harmonia.paper.json.MessageJson;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * Subscribes to the per-server commands channel and persists received AudioCommands
 * as AudioState in Redis so Velocity can read them during server-switch handoffs.
 */
public final class CommandSubscriber extends RedisPubSubAdapter<String, String> {
    private final JavaPlugin plugin;
    private final String serverId;
    private final String channel;
    private final RedisPublisher publisher;

    public CommandSubscriber(JavaPlugin plugin, String serverId, RedisService redis, RedisPublisher publisher) {
        this.plugin = plugin;
        this.serverId = serverId;
        this.channel = RedisKeys.channelCommands(serverId);
        this.publisher = publisher;

        redis.pubSub().addListener(this);
        redis.pubSub().async().subscribe(this.channel);
    }

    @Override
    public void message(String channel, String message) {
        if (!this.channel.equals(channel)) return;
        try {
            AudioCommand cmd = MessageJson.deserializeCommand(message);
            publisher.saveSession(toState(cmd));
        } catch (Exception e) {
            plugin.getLogger().warning("Malformed command on " + channel + ": " + e.getMessage());
        }
    }

    private AudioState toState(AudioCommand cmd) {
        Optional<String> trackId = cmd.action() == AudioAction.STOP
                ? Optional.empty()
                : Optional.of(cmd.trackId());
        return new AudioState(cmd.player(), serverId, trackId, cmd.action(), cmd.volume(), System.currentTimeMillis());
    }
}
