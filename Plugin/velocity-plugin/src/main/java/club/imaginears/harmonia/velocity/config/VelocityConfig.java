package club.imaginears.harmonia.velocity.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VelocityConfig {
    private final String relayUrl;
    private final String relaySecret;
    private final String clientUrl;

    public VelocityConfig(Path dataDir, Logger logger) throws IOException {
        Path configPath = dataDir.resolve("config.properties");
        Properties props = new Properties();

        if (!Files.exists(configPath)) {
            Files.createDirectories(dataDir);
            Properties defaults = new Properties();
            defaults.setProperty("relay-url",    "http://localhost:3000");
            defaults.setProperty("relay-secret", "change-me");
            defaults.setProperty("client-url",   "https://client.imaginears.club");
            try (OutputStream out = Files.newOutputStream(configPath)) {
                defaults.store(out, "Harmonia Velocity configuration — restart after editing");
            }
            logger.warn("Generated default config at {}. Edit before running in production.", configPath);
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        }

        this.relayUrl    = props.getProperty("relay-url",    "http://localhost:3000");
        this.relaySecret = props.getProperty("relay-secret", "change-me");
        this.clientUrl   = props.getProperty("client-url",   "https://client.imaginears.club");
    }

    public String relayUrl()    { return relayUrl; }
    public String relaySecret() { return relaySecret; }
    public String clientUrl()   { return clientUrl; }
}
