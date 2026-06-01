import { writeFileSync } from 'node:fs';
import type { Redis } from 'ioredis';
import type { AudioState } from '../src/relay/types.js';

export const RELAY_URL = process.env.RELAY_URL ?? 'http://localhost:3000';
export const REDIS_URL = process.env.REDIS_URL ?? 'redis://localhost:6379';
export const PLUGIN_SECRET = process.env.PLUGIN_SECRET ?? 'dev-secret';
export const CLIENT_URL = process.env.CLIENT_URL ?? 'http://localhost:5173';
export const DEFAULT_TRACK = 'test-tone.wav';

/**
 * Writes a mono 16-bit PCM WAV sine tone — the format Web Audio's decodeAudioData accepts.
 * Low amplitude and short, because the client loops it forever.
 */
export function generateToneWav(
    path: string,
    { freq = 440, seconds = 3, sampleRate = 44100, amplitude = 0.2 } = {},
): void {
    const numSamples = seconds * sampleRate;
    const dataSize = numSamples * 2;
    const buf = Buffer.alloc(44 + dataSize);

    buf.write('RIFF', 0);
    buf.writeUInt32LE(36 + dataSize, 4);
    buf.write('WAVE', 8);
    buf.write('fmt ', 12);
    buf.writeUInt32LE(16, 16);              // PCM fmt chunk size
    buf.writeUInt16LE(1, 20);               // audio format = PCM
    buf.writeUInt16LE(1, 22);               // channels = mono
    buf.writeUInt32LE(sampleRate, 24);
    buf.writeUInt32LE(sampleRate * 2, 28);  // byte rate
    buf.writeUInt16LE(2, 32);               // block align
    buf.writeUInt16LE(16, 34);              // bits per sample
    buf.write('data', 36);
    buf.writeUInt32LE(dataSize, 40);

    for (let i = 0; i < numSamples; i++) {
        const sample = Math.sin((2 * Math.PI * freq * i) / sampleRate) * amplitude;
        buf.writeInt16LE(Math.round(sample * 32767), 44 + i * 2);
    }
    writeFileSync(path, buf);
}

/**
 * Seeds the AudioState that Paper would normally persist, so the relay replays a PLAY
 * command on connect. Field-for-field match with relay/types.ts AudioState is load-bearing:
 * a mismatch parses fine but fails the relay's shouldReplay() gate silently.
 */
export function seedSession(redis: Redis, player: string, trackId: string): Promise<unknown> {
    const state: AudioState = {
        player,
        server: 'dev',
        trackId,
        action: 'PLAY',
        volume: 0.8,
        startedAt: Date.now(),
    };
    return redis.set(`harmonia:session:${player}`, JSON.stringify(state));
}
