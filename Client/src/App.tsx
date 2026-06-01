import { useEffect, useRef, useState } from 'react';
import { AudioEngine } from './audio/AudioEngine.js';
import { RelayClient, type RelayStatus } from './relay/RelayClient.js';

const STATUS_TEXT: Record<RelayStatus, string> = {
    connecting: 'Connecting…',
    connected: 'Connected',
    error: 'Connection error',
};

export default function App() {
    const engineRef = useRef<AudioEngine | null>(null);
    const startedRef = useRef(false);
    const [status, setStatus] = useState<RelayStatus>('connecting');
    const [detail, setDetail] = useState<string>();
    const [enabled, setEnabled] = useState(false);

    useEffect(() => {
        const token = new URLSearchParams(window.location.search).get('token');
        if (!token) {
            setStatus('error');
            setDetail('Missing token — open the link from in-game.');
            return;
        }

        // Connect exactly once for the page's lifetime. The URL token is single-use, so the
        // StrictMode mount→cleanup→mount cycle must not open a second socket (it would re-present
        // the already-consumed token and fail). No cleanup disconnect: the socket lives until the
        // browser tears it down on unload.
        if (startedRef.current) return;
        startedRef.current = true;

        const engine = new AudioEngine();
        const client = new RelayClient(engine, (s, d) => {
            setStatus(s);
            setDetail(d);
        });
        engineRef.current = engine;
        client.connect(token);
    }, []);

    // Autoplay policy: the AudioContext stays suspended until a user gesture resumes it.
    const enableAudio = () => {
        engineRef.current?.resume();
        setEnabled(true);
    };

    return (
        <div id="harmonia" style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', textAlign: 'center' }}>
            <h1>Harmonia</h1>
            <p>
                Status: <strong>{STATUS_TEXT[status]}</strong>
                {detail ? ` — ${detail}` : ''}
            </p>
            {!enabled && status !== 'error' && (
                <button type="button" onClick={enableAudio} style={{ padding: '0.75rem 1.5rem', fontSize: '1rem' }}>
                    🔊 Click to enable audio
                </button>
            )}
            {enabled && <p>Audio enabled. Walk into an audio region to hear it.</p>}
        </div>
    );
}
