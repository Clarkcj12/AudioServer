/** Mirrors the message types defined in the Java core module and Relay/src/relay/types.ts. */

export type AudioAction = 'PLAY' | 'STOP' | 'PAUSE' | 'RESUME' | 'SET_VOLUME';
export type RegionEventType = 'ENTER' | 'EXIT';

/** Relay → Client: perform an audio action (live, or replayed on connect). */
export interface AudioCommand {
    player: string;
    source: string;
    trackId: string;
    action: AudioAction;
    volume: number;
}

/** Relay → Client: player entered/exited an audio region. The client plays on ENTER, stops on EXIT. */
export interface RegionEvent {
    player: string;
    source: string;
    regionId: string;
    type: RegionEventType;
    trackId: string | null;   // track bound to the region via the harmonia-audio flag
}

/** Relay → Client: the player's own position, at 5 Hz, used to orient the AudioListener. */
export interface PositionUpdate {
    player: string;
    source: string;
    x: number;
    y: number;
    z: number;
    yaw: number;      // degrees
    pitch: number;    // degrees
    world: string;
}

/** Relay → Client: sent once after the token validates. */
export interface SessionReady {
    player: string;
}
