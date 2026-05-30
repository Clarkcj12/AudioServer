package club.imaginears.harmonia.core.message;

import java.util.UUID;

/**
 * Instructs a player's client to perform an audio action.
 * Sent Paper → Velocity and published to Redis for the Relay to consume.
 */
public record AudioCommand(
        UUID player,
        String source,
        String trackId,
        AudioAction action,
        float volume
) implements HarmoniaMessage {}
