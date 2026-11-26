# CLAUDE.md

This is a fork (`mstrent/OpenWatch`) of `Hale-MTU/OpenWatch`, on branch
`pixel-watch-support`, carrying a Pixel Watch 5 support effort across several AI
assistants and machines.

**Before doing anything on the Pixel Watch work, read
`docs/notes/pixel-watch-status.md`** — it is the living status/memory document:
goal, decisions, verified-vs-assumed facts, next actions, and the session
journal. **Before finishing a session, update it and push** (branch
`pixel-watch-support`, `origin` = the fork). Never leave work unpushed on an
ephemeral machine.

Everything else — architecture map, invariants, rules, verification commands —
is in `AGENTS.md`, which applies as upstream wrote it except where its
"Fork" section says otherwise. Sensor facts and open survey questions live in
`docs/research/pixel-watch-sensor-map.md`; keep them there, don't restate them.
