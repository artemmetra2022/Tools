# Build fix

## Root cause (2026-09-06)

CI failed with:

```
Build file 'build.gradle' line: 52
> Could not find method neoForge() for arguments [...] on root project 'Tools'
```

The `build.gradle` uses the **ModDevGradle** DSL (`neoForge { version; parchment {}; runs {}; mods {} }`),
but the applied plugin was `net.neoforged.gradle.userdev` (**NeoGradle 7**), which does not
create a `neoForge {}` extension at all — NeoGradle 7 uses top-level `runs {}` plus a plain
`implementation "net.neoforged:neoforge:<version>"` dependency instead.

## Fix

1. Plugin swapped to the one the script is written for:

   ```gradle
   id 'net.neoforged.moddev' version '2.0.146'
   ```

2. `modSource project.sourceSets.main` removed from `runs.configureEach`:
   ModDevGradle 2.0.146 has no `modSource` method in `RunModel`.
   The mod ↔ source-set binding is already declared in the top-level `mods {}` block.

3. `cache: gradle` removed from `actions/setup-java` in the workflow:
   caching is handled by `gradle/actions/setup-gradle`, and combining both is
   documented as incompatible (can fail the job in the post-save step).

Gradle stays at 8.13 (ModDevGradle requires 8.8+), Java stays at 21.

Run locally with:

```bash
gradle --no-daemon build
```

Note: with ModDevGradle 2.0.136+, when the `CI` environment variable is `true`
(set automatically on GitHub Actions), the build skips Minecraft decompilation,
so CI builds are much faster.
