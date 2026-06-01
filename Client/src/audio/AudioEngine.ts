import type { AudioAction, PositionUpdate } from '../relay/types.js';

interface TrackHandle {
    source: AudioBufferSourceNode;
    gain: GainNode;
    panner: PannerNode;
}

/**
 * Wraps the Web Audio API.
 * Manages AudioContext lifecycle, per-track gain and spatial panning.
 */
export class AudioEngine {
    private readonly ctx: AudioContext;
    private readonly masterGain: GainNode;
    private readonly tracks = new Map<string, TrackHandle>();

    constructor() {
        this.ctx = new AudioContext();
        this.masterGain = this.ctx.createGain();
        this.masterGain.connect(this.ctx.destination);
    }

    /** Must be called from a user-gesture handler to satisfy browser autoplay policy. */
    resume(): void {
        if (this.ctx.state === 'suspended') void this.ctx.resume();
    }

    /** Releases the AudioContext. Call on teardown. */
    async close(): Promise<void> {
        if (this.ctx.state !== 'closed') await this.ctx.close();
    }

    /**
     * Orients the listener from the player's own position. Minecraft is Y-up like Web Audio;
     * only Z is flipped. Forward vector per PROTOCOL.md (already in Web Audio space).
     *
     * Currently inert: region audio sources have no world coordinates yet, so nothing is
     * spatially panned. Wired now so it's correct the moment sources gain positions.
     */
    updateListener(pos: PositionUpdate): void {
        const yaw = (pos.yaw * Math.PI) / 180;
        const pitch = (pos.pitch * Math.PI) / 180;
        const fx = -Math.sin(yaw) * Math.cos(pitch);
        const fy = -Math.sin(pitch);
        const fz = -Math.cos(yaw) * Math.cos(pitch);

        const l = this.ctx.listener;
        if (l.positionX) {
            l.positionX.value = pos.x;
            l.positionY.value = pos.y;
            l.positionZ.value = -pos.z;   // Minecraft +Z = Web Audio −Z
            l.forwardX.value = fx;
            l.forwardY.value = fy;
            l.forwardZ.value = fz;
            l.upX.value = 0;
            l.upY.value = 1;
            l.upZ.value = 0;
        } else {
            // Deprecated API for older Safari.
            l.setPosition(pos.x, pos.y, -pos.z);
            l.setOrientation(fx, fy, fz, 0, 1, 0);
        }
    }

    async dispatch(trackId: string, action: AudioAction, volume: number): Promise<void> {
        switch (action) {
            case 'PLAY':    await this.play(trackId, volume); break;
            case 'STOP':    this.stop(trackId);               break;
            case 'PAUSE':   this.pause(trackId);              break;
            case 'RESUME':  this.resumeTrack(trackId);        break;
            case 'SET_VOLUME': this.setVolume(trackId, volume); break;
        }
    }

    private async play(trackId: string, volume: number): Promise<void> {
        this.stop(trackId);

        // TODO: resolve trackId to a URL via a track registry (audio hosting not built yet).
        const url = `/audio/${trackId}`;
        let buffer: AudioBuffer;
        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            buffer = await this.ctx.decodeAudioData(await response.arrayBuffer());
        } catch (err) {
            console.warn(`[audio] could not load track "${trackId}":`, err);
            return;
        }

        const source = this.ctx.createBufferSource();
        const gain = this.ctx.createGain();
        const panner = this.ctx.createPanner();

        source.buffer = buffer;
        source.loop = true;
        gain.gain.value = volume;

        source.connect(panner);
        panner.connect(gain);
        gain.connect(this.masterGain);
        source.start();

        source.onended = () => this.tracks.delete(trackId);
        this.tracks.set(trackId, { source, gain, panner });
    }

    private stop(trackId: string): void {
        const handle = this.tracks.get(trackId);
        if (!handle) return;
        handle.source.onended = null;
        try { handle.source.stop(); } catch { /* already stopped */ }
        this.tracks.delete(trackId);
    }

    private pause(trackId: string): void {
        // Web Audio has no native pause; suspend the whole context if single-track,
        // or use gain zeroing for multi-track scenarios.
        this.tracks.get(trackId)?.gain.gain.setTargetAtTime(0, this.ctx.currentTime, 0.05);
    }

    private resumeTrack(trackId: string): void {
        const handle = this.tracks.get(trackId);
        if (handle) handle.gain.gain.setTargetAtTime(handle.gain.gain.value, this.ctx.currentTime, 0.05);
    }

    private setVolume(trackId: string, volume: number): void {
        this.tracks.get(trackId)?.gain.gain.setTargetAtTime(volume, this.ctx.currentTime, 0.05);
    }
}
