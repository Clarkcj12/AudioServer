package club.imaginears.harmonia.paper.messaging;

import club.imaginears.harmonia.core.message.AudioCommand;
import club.imaginears.harmonia.core.message.HarmoniaMessage;
import club.imaginears.harmonia.core.message.MessageCodec;
import club.imaginears.harmonia.core.message.SessionSync;
import club.imaginears.harmonia.core.model.AudioState;
import club.imaginears.harmonia.paper.redis.RedisPublisher;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Optional;

/**
 * Receives a {@link SessionSync} from Velocity when a player lands here after a server switch,
 * and re-persists their {@code AudioState} so this server's view matches what the browser is
 * still playing. It deliberately does NOT command playback — the browser session is continuous
 * across the switch, so re-emitting a PLAY would restart the track audibly.
 *
 * Keyed off the message's UUID (not the Bukkit player), so it works regardless of whether this
 * fires before or after the player has fully spawned.
 */
public final class SessionSyncListener implements PluginMessageListener {
    private final JavaPlugin plugin;
    private final String serverId;
    private final RedisPublisher publisher;

    public SessionSyncListener(JavaPlugin plugin, String serverId, RedisPublisher publisher) {
        this.plugin = plugin;
        this.serverId = serverId;
        this.publisher = publisher;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!MessageCodec.CHANNEL.equals(channel)) return;

        HarmoniaMessage decoded;
        try {
            decoded = MessageCodec.decode(message);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Malformed plugin message on " + channel + ": " + e.getMessage());
            return;
        }

        if (decoded instanceof SessionSync sync) {
            Optional<AudioCommand> track = sync.activeTrack();
            if (track.isPresent()) {
                AudioCommand t = track.get();
                publisher.saveSession(new AudioState(
                        sync.player(), serverId, Optional.of(t.trackId()),
                        t.action(), t.volume(), System.currentTimeMillis()));
            }
        }
    }
}
