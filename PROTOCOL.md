# Harmonia Protocol

Single source of truth for the event schema across all four components:
**Paper plugin → Relay** (Redis pub/sub), **Velocity plugin → Relay** (Socket.IO), **Relay → Web Client** (Socket.IO).

Update this document before changing any wire format.

---

## Transport Layers

| Path                   | Transport             | Library                  |
|------------------------|-----------------------|--------------------------|
| Paper → Relay          | Redis pub/sub         | Lettuce (Java), ioredis (Node) |
| Velocity → Relay       | Socket.IO             | socket.io-client-java, socket.io |
| Relay → Web Client     | Socket.IO             | socket.io, socket.io-client |

Socket.IO was chosen deliberately for rooms, namespaces, reconnection, heartbeats, and transport fallback. Do not swap to raw WebSocket — the message framing is incompatible and would require rewriting both ends.

---

## Socket.IO Namespaces

| Namespace  | Clients              | Direction          |
|------------|----------------------|--------------------|
| `/plugin`  | Velocity plugin      | Plugin ↔ Relay     |
| `/client`  | Browser (Web Client) | Relay → Client     |

---

## Authentication

### Velocity (`/plugin` namespace)
Shared secret sent as `socket.handshake.auth` on connect:
```json
{ "secret": "<PLUGIN_SECRET>" }
```
`PLUGIN_SECRET` is set in `Relay/.env`.

### Web Client (`/client` namespace)
Short-lived token sent as `socket.handshake.auth` on connect:
```json
{ "token": "<TOKEN>" }
```

---

## Token Handoff Flow

```
Player joins network
       │
       ▼
Velocity generates token (UUID, 60 s TTL)
       │
       ▼
Velocity emits `session_link` to Relay /plugin
       │
       ▼
Relay stores token → playerUUID mapping
       │
       ▼
Velocity sends token to player in-game (plugin messaging / chat link)
       │
       ▼
Player opens browser at https://client.imaginears.club?token=<TOKEN>
       │
       ▼
Client connects to Relay /client with { auth: { token } }
       │
       ▼
Relay validates token (single-use, 60 s TTL)
       │
       ▼
Relay joins socket to room `player:{uuid}` → emits `session_ready`
```

---

## Redis Pub/Sub Interface (Paper ↔ Relay)

### Channels

| Channel                       | Publisher | Subscriber | Contents         |
|-------------------------------|-----------|------------|------------------|
| `harmonia:events`             | Paper     | Relay      | region events    |
| `harmonia:positions`          | Paper     | Relay      | position updates |
| `harmonia:commands:{serverId}`| Relay     | Paper      | audio commands   |

### `harmonia:events` — region event payload
Only regions carrying the `harmonia-audio` WorldGuard flag produce these events; the flag's
value is the `trackId`. Set in-game with `/rg flag <region> harmonia-audio <trackId>`.
```typescript
{
  player:   string;           // UUID
  source:   string;           // "paper:{serverId}"
  regionId: string;           // WorldGuard region ID
  type:     "ENTER" | "EXIT";
  trackId:  string | null;    // track bound to the region; present on ENTER (play) and EXIT (stop)
}
```

### `harmonia:positions` — position update payload
Published at **5 Hz (every 4 ticks)**. High-frequency channel kept separate from low-frequency events.
```typescript
{
  player: string;   // UUID
  source: string;   // "paper:{serverId}"
  x:      number;
  y:      number;
  z:      number;
  yaw:    number;   // degrees; 0 = south (+Z), 90 = west (−X), −90 = east (+X)
  pitch:  number;   // degrees; −90 = looking straight up, 90 = straight down
  world:  string;   // world name
}
```

### `harmonia:commands:{serverId}` — audio command payload
Sent by Relay back to Paper so Paper can update `AudioState` in Redis for session-handoff tracking.
```typescript
{
  player:  string;                                              // UUID
  source:  string;                                             // "relay"
  trackId: string;                                             // track identifier
  action:  "PLAY" | "STOP" | "PAUSE" | "RESUME" | "SET_VOLUME";
  volume:  number;                                             // 0.0 – 1.0
}
```

---

## Redis Key-Value Interface

| Key pattern                  | Value       | TTL           | Notes                              |
|------------------------------|-------------|---------------|------------------------------------|
| `harmonia:session:{uuid}`    | JSON string | 1 hour        | `AudioState` snapshot; updated whenever an `audio_command` is applied |
| `harmonia:players:{serverId}`| Redis Set   | none (managed)| UUIDs of currently online players  |

### `AudioState` JSON schema
```typescript
{
  player:    string;          // UUID
  server:    string;          // serverId
  trackId:   string | null;
  action:    AudioAction;
  volume:    number;
  startedAt: number;          // epoch ms
}
```

---

## Plugin Messaging (Paper ↔ Velocity)

Cross-server audio handoff is coordinated over Velocity's plugin-messaging channel
**`harmonia:v1`**, **not** through the relay or Redis. Messages use the binary `MessageCodec`
(version byte + type byte + fields) in the Java `core` module.

The handoff is **state synchronization, not playback resumption**: a backend switch does not
disconnect the browser (the relay session is keyed by UUID and persists), so the client keeps
playing throughout. The destination only re-persists the correct `AudioState`; it never re-emits
a `PLAY`, which would restart the track audibly.

### Flow

```
Player on server A, track T playing
        │
        ▼
A's region audio changes → Paper A sends SessionSync{activeTrack} to Velocity   (on every enter/exit)
        │
        ▼
Velocity caches player → activeTrack   (does not forward to the client)
        │
        ▼
Player switches A → B  (Velocity ServerConnectedEvent, previousServer present)
        │
        ▼
Velocity sends cached SessionSync{activeTrack} to server B
        │
        ▼
Paper B persists AudioState (harmonia:session:{uuid}) — no re-emit; client keeps playing T
```

### `SessionSync`  (bidirectional)
```
player:      UUID
source:      string         // "paper:{serverId}" or "velocity"
activeTrack: AudioCommand?  // absent = nothing playing
```
- **Paper → Velocity**, on each region audio-state change, so Velocity always holds the latest track.
- **Velocity → Paper (destination)**, on a server switch, to restore the carried track.

### `PlayerTransfer`  (reserved)
Defined in `core` but **unused in v1**. Velocity detects switches itself via `ServerConnectedEvent`
(it already knows the target), so Paper never needs to announce a transfer.

### Limitations (v1)
- Handoff fidelity depends on Paper's audio-state tracking: a player who logged in *already inside*
  a region generates no change event, so Velocity never learns the track and nothing is restored
  (same class as the join-inside-region gap).
- A region-scoped track logically continues after the switch even though the player has left the
  origin region (the EXIT correctly never fired — a switch is not a walk-out). Region-vs-global
  track distinction is v2.

---

## Socket.IO Events

### Velocity → Relay (`/plugin` namespace)

#### `session_link`
Emitted when Velocity is ready to hand a player's token to the browser.
```typescript
{
  player: string;   // UUID
  token:  string;   // single-use, 60 s TTL
}
```

#### `session_unlink`
Emitted on player disconnect or unrecoverable session end. Invalidates any pending token.
```typescript
{
  player: string;   // UUID
}
```

---

### Relay → Web Client (`/client` namespace, room `player:{uuid}`)

#### `audio_command`
Instructs the client to perform an audio action. Relay forwards this from the Relay's internal logic after processing region or position events.
```typescript
{
  player:  string;
  source:  string;
  trackId: string;
  action:  "PLAY" | "STOP" | "PAUSE" | "RESUME" | "SET_VOLUME";
  volume:  number;   // 0.0 – 1.0
}
```

#### `session_ready`
Sent once after successful token validation.
```typescript
{
  player: string;   // UUID
}
```

**Connect-time state replay (behavior, not a new event):** immediately after `session_ready`, the
relay reads the player's persisted `AudioState` (`harmonia:session:{uuid}`) and, if a track is
actively playing (`trackId` set and `action` is `PLAY`/`RESUME`), emits a single `audio_command`
with `action: "PLAY"`. This is how a player who is already inside an audio region hears it on
connect without waiting to cross a boundary. The relay emits `session_ready` + replay to the socket
**before** joining its `player:{uuid}` room, so live region events always arrive after the replay.

#### `region_event`
Forwarded verbatim from the Redis `harmonia:events` channel to the player's room. The region→track binding lives on the WorldGuard region (`harmonia-audio` flag), resolved by Paper — the relay does not map regions itself. The client plays `trackId` on `ENTER` and stops it on `EXIT`.
```typescript
{
  player:   string;           // UUID
  source:   string;           // "paper:{serverId}"
  regionId: string;           // WorldGuard region ID
  type:     "ENTER" | "EXIT";
  trackId:  string | null;    // track bound to the region
}
```

#### `position`
Forwarded from the Redis `harmonia:positions` channel to the player's room at **5 Hz**. The client uses this to orient its `AudioListener` (see *Coordinate System*).
```typescript
{
  player: string;   // UUID
  source: string;   // "paper:{serverId}"
  x:      number;
  y:      number;
  z:      number;
  yaw:    number;   // degrees
  pitch:  number;   // degrees
  world:  string;   // world name
}
```

---

## Audio Assets

`trackId` is the **filename** of an audio asset. The relay serves these as static files at
`GET /audio/{trackId}` from its `AUDIO_DIR` (e.g. region flag `harmonia-audio = castle-theme.ogg`
→ client fetches `/audio/castle-theme.ogg`). The client loads the file with `decodeAudioData`, so
assets must be a browser-decodable format (`.ogg`, `.mp3`, `.wav`, `.m4a`, `.opus`).

Audio is served **same-origin** with the client (dev: vite proxies `/audio` → relay; prod: the
gateway routes `/audio` to the relay), so `decodeAudioData` needs no CORS. Serving from a separate
origin/CDN later requires an `Access-Control-Allow-Origin` header on the audio response.

A URL-indirection registry (trackId → arbitrary/external media URL, metadata, crossfade) is the
Phase 4 media-queue model and is out of scope for v1.

---

## Coordinate System

Minecraft and Web Audio both use **Y-up**. Direct axis mapping:

| Minecraft | Web Audio | Notes                        |
|-----------|-----------|------------------------------|
| X (east)  | X         | same direction                |
| Y (up)    | Y         | same direction                |
| Z (south) | −Z        | Minecraft +Z = Web Audio −Z  |

### `AudioListener` orientation from player yaw/pitch
```
yaw_rad   = toRadians(yaw)
pitch_rad = toRadians(pitch)

forwardX = -sin(yaw_rad) * cos(pitch_rad)
forwardY = -sin(pitch_rad)
forwardZ = -cos(yaw_rad) * cos(pitch_rad)

upX = 0,  upY = 1,  upZ = 0   // Y-up, always
```

Set via `AudioContext.listener.forwardX/Y/Z` and `upX/Y/Z` AudioParams.

---

## Update Rates

| Data            | Rate     | Channel / Mechanism          |
|-----------------|----------|------------------------------|
| Position        | 5 Hz     | `harmonia:positions` Redis   |
| Region events   | on change| `harmonia:events` Redis      |
| Audio commands  | on change| `audio_command` Socket.IO    |

---

## Out of Scope (v2)

- WebRTC voice channels
- Per-region audio configuration API
- Client-side reverb / equalizer zones
