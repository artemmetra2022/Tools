# Build fix

The project uses NeoGradle `7.0.192`. This NeoGradle release publishes Gradle plugin variants for Gradle `8.13`, so running the project with Gradle `8.8` causes the `No matching variant` error and reports `org.gradle.plugin.api-version = 8.13`.

The CI workflow and Gradle wrapper are therefore pinned to Gradle `8.13` while Java remains 21.

Run locally with:

```bash
./gradlew --no-daemon build
```

or, if using an installed Gradle:

```bash
gradle --no-daemon build
```

with Gradle 8.13.
