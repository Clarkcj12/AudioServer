package club.imaginears.harmonia.paper.listener;

import club.imaginears.harmonia.core.message.AudioAction;
import club.imaginears.harmonia.core.message.RegionEvent;
import club.imaginears.harmonia.core.message.RegionEventType;
import club.imaginears.harmonia.core.model.AudioState;
import club.imaginears.harmonia.paper.redis.RedisPublisher;
import club.imaginears.harmonia.paper.region.AudioFlag;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which audio regions each player is inside and drives audio on the boundaries.
 *
 * Only regions carrying the {@code harmonia-audio} flag are tracked — ordinary WorldGuard
 * regions (spawn protection, etc.) are ignored. On enter/exit it both publishes a
 * {@link RegionEvent} (for the client) and persists {@link AudioState} (for cross-server handoff).
 *
 * <p>Overlapping audio regions are out of scope for v1: each transition writes the audio
 * state for its own region, so the most recent boundary crossed wins.
 */
public final class RegionListener implements Listener {
    private final String serverId;
    private final RedisPublisher publisher;
    /** Per player: regionId → trackId for the audio regions they are currently inside. */
    private final Map<UUID, Map<String, String>> playerRegions = new ConcurrentHashMap<>();

    public RegionListener(String serverId, RedisPublisher publisher) {
        this.serverId = serverId;
        this.publisher = publisher;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;
        checkRegions(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        checkRegions(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerRegions.remove(event.getPlayer().getUniqueId());
    }

    private void checkRegions(Player player, Location location) {
        if (location == null) return;
        RegionManager manager = WorldGuard.getInstance()
                .getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(location.getWorld()));
        if (manager == null) return;

        BlockVector3 pos = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());

        Map<String, String> current = new HashMap<>();
        for (ProtectedRegion region : manager.getApplicableRegions(pos)) {
            String track = trackOf(region);
            if (track != null) current.put(region.getId(), track);
        }

        UUID uuid = player.getUniqueId();
        Map<String, String> previous = playerRegions.getOrDefault(uuid, Map.of());
        String source = "paper:" + serverId;

        for (Map.Entry<String, String> entry : current.entrySet()) {
            if (!previous.containsKey(entry.getKey())) {
                String trackId = entry.getValue();
                publisher.publishRegionEvent(new RegionEvent(uuid, source, entry.getKey(), RegionEventType.ENTER, trackId));
                publisher.saveSession(playing(uuid, trackId));
            }
        }
        for (Map.Entry<String, String> entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                publisher.publishRegionEvent(new RegionEvent(uuid, source, entry.getKey(), RegionEventType.EXIT, entry.getValue()));
                publisher.saveSession(stopped(uuid));
            }
        }

        playerRegions.put(uuid, current);
    }

    /** The region's audio track, or {@code null} if the flag is unregistered or unset. */
    private String trackOf(ProtectedRegion region) {
        StringFlag flag = AudioFlag.flag();
        return flag == null ? null : region.getFlag(flag);
    }

    private AudioState playing(UUID player, String trackId) {
        return new AudioState(player, serverId, Optional.of(trackId), AudioAction.PLAY, 1.0f, System.currentTimeMillis());
    }

    private AudioState stopped(UUID player) {
        return new AudioState(player, serverId, Optional.empty(), AudioAction.STOP, 1.0f, System.currentTimeMillis());
    }
}
