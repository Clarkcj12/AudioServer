FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

EXPOSE 3000
# AUDIO_DIR defaults to ./audio (/app/audio) — bind-mount Relay/audio there at runtime.
CMD ["node", "dist/index.js"]
