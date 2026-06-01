import { io, type Socket } from 'socket.io-client';
import type { AudioEngine } from '../audio/AudioEngine.js';
import type { AudioCommand, RegionEvent } from './types.js';

export type RelayStatus = 'connecting' | 'connected' | 'error';

/**
 * Connects to the relay's `/client` namespace and drives the {@link AudioEngine} from
 * inbound events:
 *   - `audio_command`  → dispatched as-is (used for the connect-time state replay)
 *   - `region_event`   → ENTER plays the region's track, EXIT stops it
 *
 * `position` is intentionally NOT consumed yet: v1 region audio has no source coordinates,
 * so it's non-spatial. Moving the listener while sources sit at the origin would attenuate
 * them by distance and silence them. Wire position → engine.updateListener only once region
 * audio sources carry world positions.
 *
 * Region volume defaults to 1.0 (region events carry no volume in v1).
 */
export class RelayClient {
    private static readonly REGION_VOLUME = 1.0;

    private socket: Socket | null = null;

    constructor(
        private readonly engine: AudioEngine,
        private readonly onStatus: (status: RelayStatus, detail?: string) => void,
    ) {}

    connect(token: string): void {
        // Same-origin `/client` namespace; the /socket.io transport is proxied to the relay.
        const socket = io('/client', { auth: { token } });
        this.socket = socket;

        socket.on('connect', () => this.onStatus('connected'));
        socket.on('disconnect', () => this.onStatus('connecting'));
        // A consumed/expired single-use token lands here — the player must reopen the in-game link.
        socket.on('connect_error', (err) => this.onStatus('error', err.message));

        socket.on('audio_command', (cmd: AudioCommand) => {
            void this.engine.dispatch(cmd.trackId, cmd.action, cmd.volume);
        });

        socket.on('region_event', (event: RegionEvent) => {
            if (event.trackId === null) return;
            const action = event.type === 'ENTER' ? 'PLAY' : 'STOP';
            void this.engine.dispatch(event.trackId, action, RelayClient.REGION_VOLUME);
        });
    }

    disconnect(): void {
        this.socket?.disconnect();
        this.socket = null;
    }
}
