# Changelog

## 1.10.0

- **Much less lag from resting and landed builds.** Sable used to report a contact for every block of a hull's footprint, and this mod answered each one on every physics substep. Two flat faces pressed together are now left to Sable the way it handles them without this mod (`contactDetail = RIM`), so the contact count follows a build's outline instead of its area. Set `contactDetail = FULL` to go back.
- Contacts too slow to break anything are now turned away before a single object is created, which takes most of the garbage out of the physics step.
- **Performance presets.** `performancePreset = QUALITY | BALANCED | FAST`. `BALANCED` is the new default. `FAST` replaces the separate fast jar.
- **Destruction presets.** `destructionPreset = GENTLE | LIGHT | NORMAL | HEAVY | DEVASTATING` scales how hard everything hits and how much one crash may take.
- Presets only change settings that are still at their default, so anything set by hand keeps its value.
- Checked against Sable 2.0.6.
