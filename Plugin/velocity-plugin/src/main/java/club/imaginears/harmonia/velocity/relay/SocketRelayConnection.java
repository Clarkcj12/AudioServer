package club.imaginears.harmonia.velocity.relay;

import club.imaginears.harmonia.core.relay.RelayConnection;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.util.Map;

public final class SocketRelayConnection implements RelayConnection {
    private final Socket socket;
    private final Logger logger;

    /**
     * @param relayUrl base URL of the relay (e.g. "http://localhost:3000") — namespace "/plugin" is appended
     * @param secret   value of PLUGIN_SECRET from relay .env
     */
    public SocketRelayConnection(String relayUrl, String secret, Logger logger) {
        this.logger = logger;
        try {
            IO.Options opts = new IO.Options();
            // Map<String, String> is assignable to Object — if this fails to compile try Map<String, Object>
            opts.auth = Map.of("secret", secret);
            opts.reconnection = true;

            socket = IO.socket(relayUrl + "/plugin", opts);

            socket.on(Socket.EVENT_CONNECT,    args -> logger.info("Connected to relay."));
            socket.on(Socket.EVENT_DISCONNECT, args -> logger.warn("Disconnected from relay."));
            // Reconnect events live on the Manager (socket.io()), not the Socket, in socket.io-client-java 2.x.
            // On reconnect, existing browser sessions are still valid — no re-emit needed here.
            // If the relay was also restarted, it lost its token/session state; players reload their browser page.
            socket.io().on(Manager.EVENT_RECONNECT, args -> logger.warn("Reconnected to relay."));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid relay URL: " + relayUrl, e);
        }
    }

    @Override
    public void connect() {
        socket.connect();
    }

    @Override
    public void disconnect() {
        socket.disconnect();
    }

    @Override
    public boolean isConnected() {
        return socket.connected();
    }

    @Override
    public void emit(String event, String json) {
        try {
            // Relay receives this as a JS object, not a raw string — wrap in JSONObject.
            socket.emit(event, new JSONObject(json));
        } catch (JSONException e) {
            logger.warn("Failed to emit '{}': malformed JSON — {}", event, e.getMessage());
        }
    }

    @Override
    public void on(String event, EventHandler handler) {
        socket.on(event, args -> {
            if (args.length > 0) handler.handle(args[0].toString());
        });
    }
}
