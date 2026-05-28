I'm building a custom audio server/client system for my Minecraft network, similar to OpenAudioMc (https://github.com/Mindgamesnl/OpenAudioMc) but built entirely from scratch. I need you to act as a technical advisor throughout this project.

## Project Overview

I'm building a four-component system that streams audio to Minecraft players via their web browser (no client-side mods required):

1. **Paper Plugin** (Java) - Runs on backend game servers. Hooks into game events, tracks player positions, fires region triggers, sends audio commands for that server's players.
2. **Velocity Plugin** (Java) - Runs on the proxy. Owns session lifecycle, handles backend server transfers so audio doesn't drop when players switch servers, coordinates with Paper plugins via plugin messaging channels.
3. **Relay Server** (Node.js + TypeScript) - Socket.IO bridge between the plugins and web clients. Handles auth, message routing, and room/namespace management.
4. **Web Client** (React/Vite) - Browser-based audio player using Web Audio API for spatial sound. WebRTC for voice chat as a v2 feature.

## Tech Stack

**Plugin Layer:**
- **Java 25** (current LTS, ahead of Paper's Java 21 minimum)
- **Paper 1.21.11** API for game servers (final version before Mojang's new year-based versioning scheme starting in 2026)
- **Velocity 3.x** API for the proxy layer
- **Gradle** multi-module project with Kotlin DSL build scripts
  - `core` module: shared protocol/message types between Paper and Velocity plugins
  - `paper-plugin` module: Paper-specific implementation
  - `velocity-plugin` module: Velocity-specific implementation
- **socket.io-client-java** for relay connection — picked for Socket.IO compatibility (rooms, reconnection, transport fallback all handled)
- Hide WebSocket transport behind a `RelayConnection` interface — primarily for testability (mock implementations in tests) and future protocol flexibility. Not for Netty swapping; the performance gap doesn't justify the boilerplate for this workload.
- Velocity uses **Google Guice** for DI via `@Inject` — work with the framework, don't fight it
- Paper plugin: no DI initially; just clean classes. Add structure only when concrete pain demands it.

**Relay Server:**
- **Node.js** (latest LTS) + **TypeScript**
- **Socket.IO server** — chosen over raw `ws` because we need rooms, reconnection logic, heartbeats, and transport fallback. Writing those ourselves is reinventing the wheel.
- **Express** for HTTP endpoints (token validation, health checks, serving the web client)
- **Redis adapter** for horizontal scaling (Phase 4+, not v1)
- Future migration path: rewrite in Go (using Centrifugo or raw WebSocket) once protocol stabilizes. Socket.IO Java client and browser client stay the same regardless of backend language, so this is a contained rewrite.

**Web Client:**
- **Vite + React** + TypeScript (same stack OpenAudioMc uses; no reason to deviate)
- **Web Audio API** (AudioContext, PannerNode, GainNode) for spatial audio
- **socket.io-client** for control channel (matches the relay)
- **WebRTC** for voice chat (v2 feature, separate architecture with STUN/TURN, SDP negotiation, possibly an SFU like mediasoup or LiveKit for scale)

**Infrastructure:**
- HTTPS/WSS required for production (browser autoplay policies + WebRTC requirements + Sec-WebSocket-Protocol auth that 1.21.11 introduced)
- Valid SSL cert (no self-signed); domain name required (no IPs)
- No HTTP redirects on the client URL
- ngrok or Cloudflare Tunnel for local development testing with real Minecraft server

## Network Architecture

Player connection flow on a Velocity network:
- Player connects to Velocity proxy
- Velocity routes them to a backend Paper server (lobby, survival, minigame, etc.)
- Velocity plugin maintains the persistent identity (the relay session belongs to the player UUID, which persists across backend server switches)
- Paper plugin sends real-time position/world data for the player's current server
- When a player switches backend servers, the Velocity plugin handles handoff so audio doesn't drop

This means the **Velocity plugin owns session lifecycle** (link/unlink with the relay) and the **Paper plugin owns gameplay data** (position, region triggers, world events). They coordinate via Velocity's built-in plugin messaging channels, not through the relay.

## Build Phases (in order)

**Phase 0:** Prove core loop - static HTML + Node.js + raw `ws` server playing a sound on command. No Minecraft yet. ~2 hours of work; catches transport-layer bugs before they're hidden under multiple components. Switch to Socket.IO in Phase 1 once the basic loop is proven.

**Phase 1:** Relay server with Socket.IO, defined message protocol, token-based auth, connection routing between plugin and client sessions keyed by player UUID. Document the protocol in `PROTOCOL.md` FIRST before coding the second component.

**Phase 2:** Web client - Socket.IO connect, auth handshake, basic audio playback, handle browser autoplay gate (this will frustrate me; plan for it), then Web Audio API spatial sound with PannerNode positioning.

**Phase 3a:** Paper plugin - relay connection via socket.io-client-java, `/audio link` token generation command, player position updates on tick interval (every 4 ticks = 5Hz is plenty), `/playsound` admin command, basic region triggers via WorldGuard or custom region system.

**Phase 3b:** Velocity plugin - takes over session lifecycle, handles backend server transfers, plugin messaging channel between Velocity and Paper plugins for coordinated state.

**Phase 4:** Feature buildout - world-positioned speakers with falloff, region-based ambient audio, media queue with crossfading, then WebRTC voice chat as a separate workstream (its own architecture concerns).

**Phase 5 (optional):** Performance pass — consider Go rewrite of relay if scaling demands it. Real bottlenecks will be in browser Web Audio performance, main-thread blocking in plugins, and WebRTC signaling — not the network transport.

## Key Design Decisions Already Made

1. **Protocol-first development** - Define Socket.IO event schema in `PROTOCOL.md` before coding the second component. Single source of truth prevents drift between four components (Paper plugin, Velocity plugin, relay, web client).

2. **Token handoff pattern** - Velocity plugin generates token with TTL, stores `token → playerUUID` mapping, sends to relay. Player opens browser with token in URL, relay validates against Velocity, links browser session to UUID.

3. **Coordinate system** - Minecraft Y-up maps directly to Web Audio Y-up. Map player yaw/pitch to PannerNode listener orientation correctly (yaw → rotation around Y, pitch → tilt).

4. **Position update rate** - 5Hz (every 4 ticks) is plenty for music/ambient audio. Voice chat will need higher rates when added.

5. **Voice chat deferred** - Music/spatial audio first (WebSocket-based via Socket.IO), WebRTC voice chat as v2.

6. **No premature abstraction** - Start with plain classes/functions. Add custom DI/service managers only when concrete pain demands them. OpenAudioMc built theirs after the codebase got large; we'll do the same.

7. **Plugin messaging between Paper and Velocity** - Use Velocity's built-in plugin messaging channels for plugin-to-plugin communication rather than going through the relay. Keeps the relay focused on plugin↔client traffic.

8. **Socket.IO over raw WebSocket** - Picked deliberately for rooms, namespaces, reconnection, heartbeats, and transport fallback. Don't try to retrofit Socket.IO later — its message format differs enough that swapping mid-project means rewriting both ends.

9. **Node.js for the relay (now), Go as a future option** - Node.js gives the fastest iteration during protocol design (shared TypeScript types with the web client, best Socket.IO ecosystem). Go is a strong future rewrite candidate for performance/deployment reasons once the protocol stabilizes.

10. **socket.io-client-java over Netty-based custom transport** - The performance gap doesn't justify the boilerplate. This workload (5Hz position updates × hundreds of players = a few hundred KB/sec) is trivial for any modern WebSocket library. Real bottlenecks live elsewhere.

## Context About Me

- I have full-stack development experience (Node.js/Express, React, TypeScript)
- I've worked on authentication systems and data integration platforms
- I previously built the Imaginears Club websute (NextJS+React+TailwindCSS + MySQL Backend)
- I want to build this from scratch as a learning project, not fork OpenAudioMc
- I'm comfortable with Java but prefer modern patterns (records, sealed types, pattern matching, virtual threads) over enterprise Java bloat

## How I Want You to Help

1. **Challenge bad decisions early** - If I'm over-engineering or making a choice I'll regret, push back with reasoning, not just acknowledgment.

2. **Suggest the simplest thing that works** - Don't pull in a library when 30 lines of code solves it. Don't add a service layer when a function works.

3. **Flag security issues proactively** - Especially around token auth, Socket.IO Origin/CORS checks, the new Sec-WebSocket-Protocol auth pattern that 1.21.11 introduced, and any potential for token replay or session hijacking. Audio piped to browsers is an attack surface.

4. **Think about debuggability** - Logging, message tracing across four components (Paper, Velocity, relay, client). I need to figure out which layer broke when something fails. Suggest correlation IDs and structured logging early.

5. **Plan for the autoplay problem and HTTPS requirements upfront** - Don't let me discover these only when testing in production.

6. **Reference OpenAudioMc's architecture when relevant** - They've solved these problems; we can learn from what they did well and what we'd do differently. We're not copying their code.

7. **Use modern Java idioms** - Records for DTOs, sealed interfaces for message types, switch expressions with pattern matching, virtual threads where they fit. Java 25 gives us a lot.

8. **Use modern TypeScript idioms** on the relay and client - Discriminated unions for message types, strict null checks, no `any`. Share the message schema types between relay and client (monorepo or published types package).

9. **Respect the version transition** - 1.21.11 is the last "old format" Minecraft version. Anything after is `26.x.y.build.+` format. Keep this in mind for future-proofing the Paper build config.

10. **Don't over-promise about OpenAudioMc internals** - Their hosted backend is closed source. We know they use Socket.IO and shade Netty + Reactor into their plugin jar, but specifics beyond that are inference. Don't state internals as fact unless they're confirmed.

11. **Push back on premature optimization** - If I start asking about Netty, custom protocols, or microsecond-level perf tuning before the system actually works end-to-end, remind me that the bottlenecks will be in browser Web Audio, main-thread blocking, and WebRTC — not the WebSocket transport.

## First Task

Let's start with Phase 0: create the minimal end-to-end proof of concept. A `proof-of-concept/` directory with:
- A simple Node.js + TypeScript + raw `ws` WebSocket server (no Socket.IO yet — we want to prove the raw transport first, then upgrade)
- A static HTML page with a "Click to Enable Audio" button that connects via WebSocket and plays an audio file on command from the server
- A README explaining how to run it
- A trigger mechanism on the server side (HTTP endpoint or stdin command) to send the play command

After that works, we'll define the formal Socket.IO event schema in `PROTOCOL.md` before moving to Phase 1 (which is where Socket.IO enters the picture).

Ready to begin?
