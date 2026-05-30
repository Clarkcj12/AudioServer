package club.imaginears.harmonia.paper.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class HarmoniaConfig {
    private final String redisUrl;
    private final String serverId;

    public HarmoniaConfig(FileConfiguration config) {
        this.redisUrl = config.getString("redis-url", "redis://localhost:6379");
        this.serverId = config.getString("server-id", "default");
    }

    public String redisUrl() { return redisUrl; }
    public String serverId() { return serverId; }
}
