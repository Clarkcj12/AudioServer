package club.imaginears.harmonia.paper.json;

import club.imaginears.harmonia.core.message.AudioAction;
import club.imaginears.harmonia.core.message.AudioCommand;
import club.imaginears.harmonia.core.message.RegionEvent;
import club.imaginears.harmonia.core.model.AudioState;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** Serialises core message types to/from JSON to match the TypeScript interfaces in Relay/Client. */
public final class MessageJson {

    private MessageJson() {}

    public static String serialize(RegionEvent event) {
        JsonObject obj = new JsonObject();
        obj.addProperty("player", event.player().toString());
        obj.addProperty("source", event.source());
        obj.addProperty("regionId", event.regionId());
        obj.addProperty("type", event.type().name());
        obj.add("trackId", event.trackId() != null
                ? new com.google.gson.JsonPrimitive(event.trackId())
                : JsonNull.INSTANCE);
        return obj.toString();
    }

    public static String serialize(AudioState state) {
        JsonObject obj = new JsonObject();
        obj.addProperty("player", state.player().toString());
        obj.addProperty("server", state.server());
        obj.add("trackId", state.trackId()
                .<com.google.gson.JsonElement>map(com.google.gson.JsonPrimitive::new)
                .orElse(JsonNull.INSTANCE));
        obj.addProperty("action", state.action().name());
        obj.addProperty("volume", state.volume());
        obj.addProperty("startedAt", state.startedAt());
        return obj.toString();
    }

    public static String serializePosition(UUID player, String source, Location location) {
        World world = location.getWorld();
        JsonObject obj = new JsonObject();
        obj.addProperty("player", player.toString());
        obj.addProperty("source", source);
        obj.addProperty("x", location.getX());
        obj.addProperty("y", location.getY());
        obj.addProperty("z", location.getZ());
        obj.addProperty("yaw", location.getYaw());
        obj.addProperty("pitch", location.getPitch());
        obj.addProperty("world", world != null ? world.getName() : "unknown");
        return obj.toString();
    }

    public static AudioCommand deserializeCommand(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return new AudioCommand(
                UUID.fromString(obj.get("player").getAsString()),
                obj.get("source").getAsString(),
                obj.get("trackId").getAsString(),
                AudioAction.valueOf(obj.get("action").getAsString()),
                obj.get("volume").getAsFloat()
        );
    }
}
