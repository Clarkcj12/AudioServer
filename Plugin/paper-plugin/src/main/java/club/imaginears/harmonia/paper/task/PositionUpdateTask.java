package club.imaginears.harmonia.paper.task;

import club.imaginears.harmonia.paper.redis.RedisPublisher;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

/** Publishes every online player's position to Redis at 5 Hz (every 4 ticks). */
public final class PositionUpdateTask extends BukkitRunnable {
    private final String source;
    private final RedisPublisher publisher;

    public PositionUpdateTask(String serverId, RedisPublisher publisher) {
        this.source = "paper:" + serverId;
        this.publisher = publisher;
    }

    @Override
    public void run() {
        for (var player : Bukkit.getOnlinePlayers()) {
            publisher.publishPosition(player.getUniqueId(), source, player.getLocation());
        }
    }
}
