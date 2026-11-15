# CLAUDE.md — instructions for the next agent (Oersted 737 Line Lab, Android port)

Read this file first, then `docs/HANDOFF.md` (status + next tasks) and `docs/PORT_MAP.md` (web → Kotlin file map).
You are working on a port of a React/TypeScript web trainer to a native Android app (Kotlin + Jetpack Compose).

## Ground rules
- The web source is the **reference of truth**: `reference/web/src/...`. Do not "improve" the physics; port it.
- Port **one file at a time**, keep the same names, keep the same constants and comments (they cite AMM/ATA data).
- Every ported sim function gets a unit test in `app/src/test/java/com/oersted/trainer/sim/`.
- Do not commit generated files, `local.properties`, keystores, or `build/`.
- You cannot reach `dl.google.com`, `maven.google.com`, or `repo.maven.apache.org` from the sandbox (proxy returns 403). **You cannot compile the Android app locally.** Build verification happens in GitHub Actions (`.github/workflows/android.yml`). Pure-Kotlin code can be checked with a standalone `kotlinc` (see below).
- Keep changes small and verifiable. Commit after each phase.

## Commands
- Pure-Kotlin check (works in the sandbox, after downloading kotlin-compiler from GitHub releases):
  `kotlinc <files> -include-runtime -d out.jar && java -jar out.jar`
- Full build + tests (CI or any machine with the Android SDK): `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- Re-export static data after editing the web `src/data/*.ts`: see `tools/export-data.mts` (uses `tsx`).

## Commit / PR attribution
When you commit, end the message with the trailers the user supplied in the session (currently
`Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>` and the `Claude-Session:` line). Follow the latest instruction given in the conversation.
