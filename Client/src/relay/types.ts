/** Mirrors the sealed message types defined in the Java core module and Relay. */

export type AudioAction = 'PLAY' | 'STOP' | 'PAUSE' | 'RESUME' | 'SET_VOLUME';
export type RegionEventType = 'ENTER' | 'EXIT';

export interface AudioCommand {
    player: string;
    source: string;
    trackId: string;
    action: AudioAction;
    volume: number;
}

export interface RegionEvent {
    player: string;
    source: string;
    regionId: string;
    type: RegionEventType;
}
