/** Mirrors the sealed message types defined in the Java core module. */

export type AudioAction = 'PLAY' | 'STOP' | 'PAUSE' | 'RESUME' | 'SET_VOLUME';
export type RegionEventType = 'ENTER' | 'EXIT';

export interface AudioCommand {
    player: string;   // UUID string
    source: string;   // originating server id
    trackId: string;
    action: AudioAction;
    volume: number;
}

export interface RegionEvent {
    player: string;
    source: string;
    regionId: string;
    type: RegionEventType;
    trackId: string | null;   // track bound to the region via the harmonia-audio flag
}

/** Player position update, forwarded from the Redis `harmonia:positions` channel at 5 Hz. */
export interface PositionUpdate {
    player: string;   // UUID
    source: string;   // "paper:{serverId}"
    x: number;
    y: number;
    z: number;
    yaw: number;      // degrees
    pitch: number;    // degrees
    world: string;
}

export interface SessionSync {
    player: string;
    source: string;
    activeTrack: AudioCommand | null;
}

export interface PlayerTransfer {
    player: string;
    source: string;
    target: string;
    activeTrack: AudioCommand | null;
}

/** Velocity → Relay (`/plugin`): hand a player's single-use token to the relay. */
export interface SessionLink {
    player: string;   // UUID
    token: string;    // single-use, 60 s TTL
}

/** Velocity → Relay (`/plugin`): invalidate any pending token for a player. */
export interface SessionUnlink {
    player: string;   // UUID
}

/** Relay → Client (`/client`): sent once after a token validates. */
export interface SessionReady {
    player: string;   // UUID
}

export interface AudioState {
    player: string;
    server: string;
    trackId: string | null;
    action: AudioAction;
    volume: number;
    startedAt: number;  // epoch millis
}
