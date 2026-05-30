package club.imaginears.harmonia.core.message;

import java.util.Optional;
import java.util.UUID;

/**
 * Sent Velocity → Paper when a player lands on a backend server.
 * Carries the player's active audio state so playback resumes seamlessly.
 */
public record SessionSync(
        UUID player,
        String source,
        Optional<AudioCommand> activeTrack
) implements HarmoniaMessage {}
