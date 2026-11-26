# Hybrid model engine acceptance gates

Baseline: commit 99137887b2a18ce66640df6759898e8860a84e49, [CI passed](https://github.com/KSPOG/mythos/actions/runs/45877240897). Core Java tests are not Minecraft integration tests.

| Milestone | Status | Acceptance criteria |
|---|---|---|
| 1 Registry | Core CI passed | Validated registration, duplicate rejection, deterministic fallback; integration API documented |
| 2 Optional Forge client | Partial | Real Forge 1.21.1 handshake, authenticated connection binding, vanilla login and reconnect tests |
| 3 GLB renderer | Pending | Bounded mesh decoding, actual textured mesh on GPU, correct transforms, cleanup and visual evidence |
| 4 Vanilla fallback | Pending | Mixed clients see equivalent entities without duplicates; fallback on missing assets |
| 5 Asset sync/cache | Partial | Verified bytes, bounded downloads, atomic cache, invalidation, corrupt asset rejection |
| 6 Animation | Pending | Skeletal skinning, interpolation, blending, attachment transforms, visible in-game test |
| 7 Mounts/pets/merchants | Pending | Actual editable textured animated dragon and fallback, server-authoritative integration |
| 8 Equipment/player | Pending | Distinct detailed armor, weapon and player meshes, correct poses, class restrictions unchanged |
| 9 Studio/launcher | Pending | Editable asset workflow, preview, optional installation and update verification |
| 10 Performance/multiplayer | Pending | Mixed-client session, reconnect, stress and frame-time measurements, documented hardware and limits |

Do not mark an integration milestone complete based solely on standalone unit tests. Keep vanilla access and server authority. Record commit and CI links plus in-game evidence when available.

## Asset cache increment

Content-addressed local cache implemented with bounded writes, SHA-256 verification, atomic publication, and corrupt-file recovery tests. Transport, total cache quota, eviction and runtime integration remain outstanding. CI pending for this increment.

## Cache quota increment

Previous cache CI passed at [928244e](https://github.com/KSPOG/mythos/actions/runs/45892300131). Added configurable published-cache quota (default 512 MiB) and oldest-modified-first eviction after successful verification. One private cache instance per root is required; temporary download space is additional (up to one asset). Transport, crash cleanup, active asset lifetime and runtime integration remain open. New quota tests await CI.
