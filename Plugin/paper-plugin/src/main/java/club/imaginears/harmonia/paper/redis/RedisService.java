package club.imaginears.harmonia.paper.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

public final class RedisService {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;

    public RedisService(String redisUrl) {
        client = RedisClient.create(redisUrl);
        connection = client.connect();
        pubSubConnection = client.connectPubSub();
    }

    /** Returns async commands for publishing, key-value ops, and set operations. */
    public RedisAsyncCommands<String, String> async() {
        return connection.async();
    }

    /** Returns the dedicated pub/sub connection for subscriptions. */
    public StatefulRedisPubSubConnection<String, String> pubSub() {
        return pubSubConnection;
    }

    public void shutdown() {
        connection.closeAsync();
        pubSubConnection.closeAsync();
        client.shutdownAsync();
    }
}
