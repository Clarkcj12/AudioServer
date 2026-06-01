package club.imaginears.harmonia.paper;

import club.imaginears.harmonia.core.message.MessageCodec;
import club.imaginears.harmonia.paper.config.HarmoniaConfig;
import club.imaginears.harmonia.paper.listener.PlayerConnectionListener;
import club.imaginears.harmonia.paper.listener.RegionListener;
import club.imaginears.harmonia.paper.messaging.PluginMessenger;
import club.imaginears.harmonia.paper.messaging.SessionSyncListener;
import club.imaginears.harmonia.paper.redis.CommandSubscriber;
import club.imaginears.harmonia.paper.redis.RedisPublisher;
import club.imaginears.harmonia.paper.redis.RedisService;
import club.imaginears.harmonia.paper.region.AudioFlag;
import club.imaginears.harmonia.paper.task.PositionUpdateTask;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class HarmoniaPlugin extends JavaPlugin {
    private RedisService redisService;
    private BukkitTask positionTask;

    @Override
    public void onLoad() {
        // Custom WorldGuard flags must be registered before WG loads its regions (its onEnable),
        // so this happens in onLoad — never onEnable. Guarded so we never touch WG classes when absent.
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            AudioFlag.register(getLogger());
            getLogger().info("Registered WorldGuard flag '" + AudioFlag.NAME + "'.");
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        HarmoniaConfig config = new HarmoniaConfig(getConfig());

        redisService = new RedisService(config.redisUrl());
        RedisPublisher publisher = new RedisPublisher(redisService, config.serverId());
        new CommandSubscriber(this, config.serverId(), redisService, publisher);

        // Paper ↔ Velocity plugin messaging (harmonia:v1) for cross-server audio handoff.
        getServer().getMessenger().registerOutgoingPluginChannel(this, MessageCodec.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, MessageCodec.CHANNEL, new SessionSyncListener(this, config.serverId(), publisher));
        PluginMessenger messenger = new PluginMessenger(this, config.serverId());

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(publisher), this);

        if (pm.isPluginEnabled("WorldGuard")) {
            pm.registerEvents(new RegionListener(config.serverId(), publisher, messenger), this);
            getLogger().info("WorldGuard region tracking enabled.");
        }

        // 5 Hz — every 4 ticks. Initial delay of 4 ticks so the first tick isn't on the same frame as onEnable.
        positionTask = new PositionUpdateTask(config.serverId(), publisher).runTaskTimer(this, 4L, 4L);

        getLogger().info("Harmonia enabled.");
    }

    @Override
    public void onDisable() {
        if (positionTask != null) positionTask.cancel();
        if (redisService != null) redisService.shutdown();
        getLogger().info("Harmonia disabled.");
    }
}
