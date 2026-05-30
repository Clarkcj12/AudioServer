package club.imaginears.harmonia.core.relay;

/**
 * Abstracts the plugin-to-relay transport.
 * The production implementation wraps socket.io-client-java.
 * Test implementations can be plain mocks without any network dependency.
 */
public interface RelayConnection {

    void connect();
    void disconnect();
    boolean isConnected();

    /** Emits a named event with a JSON-encoded payload to the relay. */
    void emit(String event, String json);

    /** Registers a handler for inbound events from the relay. */
    void on(String event, EventHandler handler);

    @FunctionalInterface
    interface EventHandler {
        void handle(String json);
    }
}
