package club.imaginears.harmonia.velocity.listener;

import club.imaginears.harmonia.core.message.HarmoniaMessage;
import club.imaginears.harmonia.core.message.MessageCodec;
import club.imaginears.harmonia.core.message.SessionSync;
import club.imaginears.harmonia.velocity.session.AudioStateCache;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Cross-server audio handoff. Caches the active track each backend reports, and replays it to the
 * destination when the player switches servers — so audio state survives the switch. The browser
 * session is continuous, so this only re-syncs state; it never restarts playback.
 */
public final class AudioHandoffListener {
    private final AudioStateCache cache;
    private final ChannelIdentifier channel;
    private final Logger logger;

    public AudioHandoffListener(AudioStateCache cache, ChannelIdentifier channel, Logger logger) {
        this.cache = cache;
        this.channel = channel;
        this.logger = logger;
    }

    /** Paper → Velocity: a backend reports the player's active track. Cache it; don't forward to the client. */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) return;
        // Only trust messages from a backend server, never from a client.
        if (!(event.getSource() instanceof ServerConnection)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        HarmoniaMessage msg;
        try {
            msg = MessageCodec.decode(event.getData());
        } catch (RuntimeException e) {
            logger.warn("Malformed plugin message on {}: {}", channel.getId(), e.getMessage());
            return;
        }
        if (msg instanceof SessionSync sync) {
            cache.update(sync.player(), sync.activeTrack());
        }
    }

    /** Velocity → destination Paper: on a server switch, hand over the cached track. */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getPreviousServer().isEmpty()) return;   // only a switch, not the first connect

        Player player = event.getPlayer();
        Optional<ServerConnection> target = player.getCurrentServer();
        cache.get(player.getUniqueId()).ifPresent(track -> target.ifPresent(server -> {
            SessionSync sync = new SessionSync(player.getUniqueId(), "velocity", Optional.of(track));
            server.sendPluginMessage(channel, MessageCodec.encode(sync));
        }));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        cache.clear(event.getPlayer().getUniqueId());
    }
}
