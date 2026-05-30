package club.imaginears.harmonia.velocity.listener;

import club.imaginears.harmonia.velocity.session.SessionManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;

public final class PlayerSessionListener {
    private final SessionManager sessionManager;

    public PlayerSessionListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        sessionManager.onPlayerJoin(event.getPlayer());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        // Only send chat link on first backend connection — not on server switches
        if (event.getPreviousServer().isEmpty()) {
            sessionManager.sendAudioLink(event.getPlayer());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        sessionManager.onPlayerLeave(event.getPlayer());
    }
}
