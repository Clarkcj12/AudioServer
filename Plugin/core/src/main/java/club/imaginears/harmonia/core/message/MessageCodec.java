package club.imaginears.harmonia.core.message;

import java.io.*;
import java.util.Optional;
import java.util.UUID;

/** Encodes and decodes {@link HarmoniaMessage} for the {@code harmonia:v1} plugin messaging channel. */
public final class MessageCodec {

    public static final String CHANNEL = "harmonia:v1";
    private static final byte VERSION = 1;

    private static final int TYPE_AUDIO_COMMAND   = 0;
    private static final int TYPE_REGION_EVENT    = 1;
    private static final int TYPE_SESSION_SYNC    = 2;
    private static final int TYPE_PLAYER_TRANSFER = 3;

    private MessageCodec() {}

    public static byte[] encode(HarmoniaMessage message) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(buf)) {

            out.writeByte(VERSION);

            switch (message) {
                case AudioCommand m -> {
                    out.writeByte(TYPE_AUDIO_COMMAND);
                    writeUUID(out, m.player());
                    out.writeUTF(m.source());
                    writeTrack(out, m);
                }
                case RegionEvent m -> {
                    out.writeByte(TYPE_REGION_EVENT);
                    writeUUID(out, m.player());
                    out.writeUTF(m.source());
                    out.writeUTF(m.regionId());
                    out.writeByte(m.type().ordinal());
                    writeNullableUTF(out, m.trackId());
                }
                case SessionSync m -> {
                    out.writeByte(TYPE_SESSION_SYNC);
                    writeUUID(out, m.player());
                    out.writeUTF(m.source());
                    writeOptionalTrack(out, m.activeTrack());
                }
                case PlayerTransfer m -> {
                    out.writeByte(TYPE_PLAYER_TRANSFER);
                    writeUUID(out, m.player());
                    out.writeUTF(m.source());
                    out.writeUTF(m.target());
                    writeOptionalTrack(out, m.activeTrack());
                }
            }

            return buf.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode HarmoniaMessage", e);
        }
    }

    public static HarmoniaMessage decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported protocol version: " + version);
            }

            int type = in.readUnsignedByte();
            return switch (type) {
                case TYPE_AUDIO_COMMAND -> {
                    UUID player = readUUID(in);
                    String source = in.readUTF();
                    yield readTrack(in, player, source);
                }
                case TYPE_REGION_EVENT -> {
                    UUID player = readUUID(in);
                    String source = in.readUTF();
                    String regionId = in.readUTF();
                    RegionEventType eventType = RegionEventType.values()[in.readUnsignedByte()];
                    String trackId = readNullableUTF(in);
                    yield new RegionEvent(player, source, regionId, eventType, trackId);
                }
                case TYPE_SESSION_SYNC -> {
                    UUID player = readUUID(in);
                    String source = in.readUTF();
                    Optional<AudioCommand> track = readOptionalTrack(in, player, source);
                    yield new SessionSync(player, source, track);
                }
                case TYPE_PLAYER_TRANSFER -> {
                    UUID player = readUUID(in);
                    String source = in.readUTF();
                    String target = in.readUTF();
                    Optional<AudioCommand> track = readOptionalTrack(in, player, source);
                    yield new PlayerTransfer(player, source, target, track);
                }
                default -> throw new IllegalArgumentException("Unknown message type: " + type);
            };
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode HarmoniaMessage", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void writeUUID(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUUID(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeTrack(DataOutputStream out, AudioCommand cmd) throws IOException {
        out.writeUTF(cmd.trackId());
        out.writeByte(cmd.action().ordinal());
        out.writeFloat(cmd.volume());
    }

    private static AudioCommand readTrack(DataInputStream in, UUID player, String source) throws IOException {
        String trackId = in.readUTF();
        AudioAction action = AudioAction.values()[in.readUnsignedByte()];
        float volume = in.readFloat();
        return new AudioCommand(player, source, trackId, action, volume);
    }

    private static void writeNullableUTF(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) out.writeUTF(value);
    }

    private static String readNullableUTF(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeOptionalTrack(DataOutputStream out, Optional<AudioCommand> track) throws IOException {
        out.writeBoolean(track.isPresent());
        if (track.isPresent()) writeTrack(out, track.get());
    }

    private static Optional<AudioCommand> readOptionalTrack(DataInputStream in, UUID player, String source) throws IOException {
        return in.readBoolean() ? Optional.of(readTrack(in, player, source)) : Optional.empty();
    }
}
