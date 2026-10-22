# Kotlin runtime notices

The following versions were observed in the Windows `releaseRuntimeClasspath` report. The release build checks its resolved artifacts and fails if an unlisted external runtime component appears.

| Maven module | Resolved version | License |
| --- | --- | --- |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.1.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.7.3 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 | Apache-2.0 |
| `org.jetbrains:annotations` (when present) | 13.0 | Apache-2.0 |

`org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` is the declared multiplatform coordinate; its JVM runtime artifact is `kotlinx-serialization-json-jvm`. The corresponding core JVM artifact is transitive. `kotlinx-serialization-bom:1.7.3` is version metadata in the published Gradle module, not an APK library. The Kotlin Gradle plugin is a build tool, not a runtime artifact.

## Kotlin standard library

Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors. Licensed under Apache-2.0; see `APACHE-2.0.txt`.

The Kotlin 2.1.0 license inventory identifies code derived from:

- GWT collections: Copyright (C) 2007-08 Google Inc.; Apache-2.0.
- Guava UnsignedLongs: Copyright (C) 2011 The Guava Authors; Apache-2.0.
- Boost special math functions: Copyright Eric Ford and Hubert Holin 2001; Boost Software License 1.0, see `BOOST-1.0.txt`.

Source and detailed inventory: https://github.com/JetBrains/kotlin/blob/v2.1.0/license/README.md (especially `libraries/stdlib/` entries). The upstream license text is https://github.com/JetBrains/kotlin/blob/v2.1.0/license/LICENSE.txt.

## kotlinx.serialization JSON and core

The JSON `Json.kt` source header states Copyright 2017-2024 JetBrains s.r.o.; the core `Annotations.kt` source header states Copyright 2017-2023 JetBrains s.r.o. The libraries are Apache-2.0; see `APACHE-2.0.txt`. Individual source headers remain in the upstream source.

Source: https://github.com/Kotlin/kotlinx.serialization/tree/v1.7.3 . License: https://github.com/Kotlin/kotlinx.serialization/blob/v1.7.3/LICENSE.txt .

## JetBrains annotations

The `NotNull.java` source header states Copyright 2000-2012 JetBrains s.r.o. Maven metadata for `org.jetbrains:annotations:13.0` identifies Apache-2.0; see `APACHE-2.0.txt`.

Artifact metadata: https://repo.maven.apache.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.pom .
