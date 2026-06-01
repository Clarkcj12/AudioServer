package club.imaginears.harmonia.paper.messaging;

import club.imaginears.harmonia.core.message.AudioCommand;
import club.imaginears.harmonia.core.message.MessageCodec;
import club.imaginears.harmonia.core.message.SessionSync;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * Sends a player's current audio state to Velocity over the {@code harmonia:v1} channel,
 * so Velocity can hand it to the next backend server when the player switches.
 */
public final class PluginMessenger {
    private final JavaPlugin plugin;
    private final String source;

    public PluginMessenger(JavaPlugin plugin, String serverId) {
        this.plugin = plugin;
        this.source = "paper:" + serverId;
    }

    /** Reports the player's active track (or {@code empty} when nothing is playing) to Velocity. */
    public void syncActiveTrack(Player player, Optional<AudioCommand> activeTrack) {
        SessionSync msg = new SessionSync(player.getUniqueId(), source, activeTrack);
        player.sendPluginMessage(plugin, MessageCodec.CHANNEL, MessageCodec.encode(msg));
    }
}
