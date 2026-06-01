package club.imaginears.harmonia.velocity.session;

import club.imaginears.harmonia.core.message.AudioCommand;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds each player's currently-active audio track, as reported by their backend Paper server.
 * Read when the player switches servers, to hand the track to the destination.
 */
public final class AudioStateCache {
    private final ConcurrentHashMap<UUID, AudioCommand> active = new ConcurrentHashMap<>();

    /** Records (or clears, when empty) the player's active track from a Paper SessionSync. */
    public void update(UUID player, Optional<AudioCommand> track) {
        track.ifPresentOrElse(t -> active.put(player, t), () -> active.remove(player));
    }

    public Optional<AudioCommand> get(UUID player) {
        return Optional.ofNullable(active.get(player));
    }

    public void clear(UUID player) {
        active.remove(player);
    }
}
