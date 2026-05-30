package club.imaginears.harmonia.paper.listener;

import club.imaginears.harmonia.core.message.AudioAction;
import club.imaginears.harmonia.core.model.AudioState;
import club.imaginears.harmonia.paper.redis.RedisPublisher;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

public final class PlayerConnectionListener implements Listener {
    private final String serverId;
    private final RedisPublisher publisher;

    public PlayerConnectionListener(String serverId, RedisPublisher publisher) {
        this.serverId = serverId;
        this.publisher = publisher;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        publisher.trackPlayer(uuid);
        // Seed a silent state; a SessionSync from Velocity will overwrite it if a track is active.
        publisher.saveSession(new AudioState(
                uuid, serverId, Optional.empty(), AudioAction.STOP, 0f, System.currentTimeMillis()
        ));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        publisher.untrackPlayer(uuid);
        publisher.deleteSession(uuid);
    }
}
