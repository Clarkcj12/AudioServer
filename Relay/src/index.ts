import { createServer } from './server.js';

const PORT = parseInt(process.env.PORT ?? '3000', 10);

const httpServer = createServer();
httpServer.listen(PORT, () => {
    console.log(`Harmonia Relay listening on :${PORT}`);
});
