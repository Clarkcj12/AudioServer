package club.imaginears.harmonia.core.model;

import club.imaginears.harmonia.core.message.AudioAction;

import java.util.Optional;
import java.util.UUID;

/**
 * Snapshot of a player's audio playback state, persisted in Redis.
 * Serialisation to/from JSON is handled by each platform module so that
 * the Relay (Node.js) can also read this structure.
 */
public record AudioState(
        UUID player,
        String server,
        Optional<String> trackId,
        AudioAction action,
        float volume,
        long startedAt
) {
    public boolean isPlaying() {
        return trackId.isPresent()
                && (action == AudioAction.PLAY || action == AudioAction.RESUME);
    }
}
