package club.imaginears.harmonia.velocity.session;

import club.imaginears.harmonia.core.relay.RelayConnection;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private final String clientUrl;
    private final RelayConnection relay;
    private final Logger logger;

    // playerUUID → token; single-use enforcement is the relay's responsibility (TokenService)
    private final ConcurrentHashMap<UUID, String> playerTokens = new ConcurrentHashMap<>();

    public SessionManager(String clientUrl, RelayConnection relay, Logger logger) {
        this.clientUrl = clientUrl;
        this.relay = relay;
        this.logger = logger;
    }

    public void onPlayerJoin(Player player) {
        String token = UUID.randomUUID().toString();
        playerTokens.put(player.getUniqueId(), token);
        relay.emit("session_link", String.format(
            "{\"player\":\"%s\",\"token\":\"%s\"}",
            player.getUniqueId(), token
        ));
        logger.debug("session_link emitted for {} ({})", player.getUsername(), player.getUniqueId());
    }

    public void sendAudioLink(Player player) {
        String token = playerTokens.get(player.getUniqueId());
        if (token == null) return;

        String url = clientUrl + "?token=" + token;
        player.sendMessage(
            Component.text("[Harmonia] ", NamedTextColor.GOLD)
                .append(Component.text("Click here to enable spatial audio", NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.openUrl(url)))
        );
    }

    public void onPlayerLeave(Player player) {
        String token = playerTokens.remove(player.getUniqueId());
        if (token == null) return;
        relay.emit("session_unlink", String.format(
            "{\"player\":\"%s\"}",
            player.getUniqueId()
        ));
        logger.debug("session_unlink emitted for {} ({})", player.getUsername(), player.getUniqueId());
    }
}
