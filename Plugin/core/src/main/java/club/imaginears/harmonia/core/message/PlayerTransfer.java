package club.imaginears.harmonia.core.message;

import java.util.Optional;
import java.util.UUID;

/**
 * Reserved, unused in v1. Velocity detects server switches itself via {@code ServerConnectedEvent}
 * (it already knows the target), so Paper never needs to announce a transfer — the active track is
 * synced continuously via {@link SessionSync} instead.
 */
public record PlayerTransfer(
        UUID player,
        String source,
        String target,
        Optional<AudioCommand> activeTrack
) implements HarmoniaMessage {}
