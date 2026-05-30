import type { AudioAction } from '../relay/types.js';

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

        // TODO: resolve trackId to a URL via a track registry
        const url = `/audio/${trackId}`;
        const response = await fetch(url);
        const buffer = await this.ctx.decodeAudioData(await response.arrayBuffer());

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
