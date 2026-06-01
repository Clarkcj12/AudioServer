package club.imaginears.harmonia.paper.listener;

import club.imaginears.harmonia.paper.redis.RedisPublisher;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {
    private final RedisPublisher publisher;

    public PlayerConnectionListener(RedisPublisher publisher) {
        this.publisher = publisher;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        publisher.trackPlayer(uuid);
        // No silent seed here: on a server switch a SessionSync from Velocity restores the active
        // track, and seeding STOP would race it (plugin-message vs join ordering isn't guaranteed).
        // Absence of a session key already means silence — the relay's replay finds nothing.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        publisher.untrackPlayer(uuid);
        publisher.deleteSession(uuid);
    }
}
