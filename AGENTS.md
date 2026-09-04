# Combat Replay Development Notes

- Follow RuneLite Plugin Hub conventions and target Java 11.
- Record only state exposed by the RuneLite API; do not infer that observations are authoritative server state.
- Keep the recording model independent of live RuneLite objects so saved recordings remain replayable.
- Format v1 records exact names for all visible players. Treat names as sensitive replay content: disclose capture/upload in the UI and docs, and keep names out of logs, filenames derived without sanitization, analytics, routes, and cross-replay indexes.
- Do not use reflection, native access, process execution, input injection, or dynamic code loading.
- A clean build does not validate capture accuracy; recordings must be inspected in-game.
