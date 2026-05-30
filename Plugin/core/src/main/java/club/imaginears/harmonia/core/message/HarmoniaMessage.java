package club.imaginears.harmonia.core.message;

import java.util.UUID;

/** Base type for all Harmonia plugin-messaging-channel messages. */
public sealed interface HarmoniaMessage
        permits AudioCommand, RegionEvent, SessionSync, PlayerTransfer {

    UUID player();
    String source();
}
