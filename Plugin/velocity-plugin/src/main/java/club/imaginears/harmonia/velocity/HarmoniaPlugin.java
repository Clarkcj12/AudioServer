package club.imaginears.harmonia.velocity;

import club.imaginears.harmonia.core.message.MessageCodec;
import club.imaginears.harmonia.velocity.config.VelocityConfig;
import club.imaginears.harmonia.velocity.listener.AudioHandoffListener;
import club.imaginears.harmonia.velocity.listener.PlayerSessionListener;
import club.imaginears.harmonia.velocity.relay.SocketRelayConnection;
import club.imaginears.harmonia.velocity.session.AudioStateCache;
import club.imaginears.harmonia.velocity.session.SessionManager;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

@Plugin(
    id = "harmonia",
    name = "Harmonia",
    version = "1.0-SNAPSHOT",
    url = "https://imaginears.club"
)
public final class HarmoniaPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDir;

    private SocketRelayConnection relay;

    @Inject
    public HarmoniaPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDir) {
        this.server = server;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        VelocityConfig config;
        try {
            config = new VelocityConfig(dataDir, logger);
        } catch (IOException e) {
            logger.error("Failed to load config — disabling Harmonia.", e);
            return;
        }

        relay = new SocketRelayConnection(config.relayUrl(), config.relaySecret(), logger);
        relay.connect();

        SessionManager sessionManager = new SessionManager(config.clientUrl(), relay, logger);
        server.getEventManager().register(this, new PlayerSessionListener(sessionManager));

        // Cross-server audio handoff over the harmonia:v1 plugin-messaging channel.
        MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.from(MessageCodec.CHANNEL);
        server.getChannelRegistrar().register(channel);
        server.getEventManager().register(this, new AudioHandoffListener(new AudioStateCache(), channel, logger));

        logger.info("Harmonia enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (relay != null) relay.disconnect();
        logger.info("Harmonia disabled.");
    }
}
