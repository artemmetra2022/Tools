# Build fix

The original project did not contain `gradle/wrapper/gradle-wrapper.jar`. The CI fallback generated a wrapper with the runner's installed Gradle 9.7.1, while this project uses the older NeoGradle userdev 7.0.x toolchain.

That caused:
`org.gradle.api.problems.ProblemReporter ... Problems.forNamespace(java.lang.String)`

The fixed project pins Gradle 8.8 in CI and keeps the wrapper distribution on Gradle 8.8. The build workflow does not depend on the missing wrapper JAR.
