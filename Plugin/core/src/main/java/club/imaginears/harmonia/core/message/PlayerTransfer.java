package club.imaginears.harmonia.core.message;

import java.util.Optional;
import java.util.UUID;

/**
 * Sent Paper → Velocity when a server-switch is initiated.
 * Velocity stores the active track and delivers it via {@link SessionSync} on the destination.
 */
public record PlayerTransfer(
        UUID player,
        String source,
        String target,
        Optional<AudioCommand> activeTrack
) implements HarmoniaMessage {}
