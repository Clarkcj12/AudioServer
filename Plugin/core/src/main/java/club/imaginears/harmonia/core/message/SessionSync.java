package club.imaginears.harmonia.core.message;

import java.util.Optional;
import java.util.UUID;

/**
 * Carries a player's active audio track over the {@code harmonia:v1} channel. Bidirectional:
 *  - Paper → Velocity on each region audio-state change (Velocity caches the latest track);
 *  - Velocity → Paper (destination) on a server switch, to restore the carried track.
 *
 * The destination re-persists {@code AudioState}; it never re-emits playback (the browser
 * session is continuous across the switch, so the track keeps playing).
 */
public record SessionSync(
        UUID player,
        String source,
        Optional<AudioCommand> activeTrack
) implements HarmoniaMessage {}
