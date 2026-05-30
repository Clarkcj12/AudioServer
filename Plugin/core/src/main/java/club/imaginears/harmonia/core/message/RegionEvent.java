package club.imaginears.harmonia.core.message;

import java.util.UUID;

/**
 * Fired when a player enters or exits a WorldGuard audio region.
 *
 * @param trackId the track bound to the region via the {@code harmonia-audio} flag.
 *                Present on both ENTER (what to play) and EXIT (what to stop). Never null
 *                in practice — only audio-flagged regions produce these events.
 */
public record RegionEvent(
        UUID player,
        String source,
        String regionId,
        RegionEventType type,
        String trackId
) implements HarmoniaMessage {}
