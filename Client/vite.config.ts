import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            '/socket.io': {
                target: 'http://localhost:3000',
                ws: true,
            },
            // Audio assets are served by the relay; keep them same-origin so decodeAudioData needs no CORS.
            '/audio': {
                target: 'http://localhost:3000',
            },
        },
    },
});
