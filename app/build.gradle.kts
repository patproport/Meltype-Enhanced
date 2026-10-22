plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.horota.meltype"
    compileSdk = 35
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "io.github.patproport.meltypeenhanced"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
        // 16KB のページサイズの端末 (Android 15 以降の要件) でも読み込めるようにする
        externalNativeBuild { cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" } }
    }

    signingConfigs {
        create("release") {
            val keystore = System.getenv("MELTYPE_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("MELTYPE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MELTYPE_KEY_ALIAS")
                keyPassword = System.getenv("MELTYPE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (System.getenv("MELTYPE_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    externalNativeBuild {
        cmake { version = "3.22.1"; path = file("src/main/cpp/CMakeLists.txt") }
    }
    // Task 2 の成果物 (libMeltypeNative.so, libmozc_helper.so) を APK に入れる
    sourceSets["main"].jniLibs.srcDirs("../build/jniLibs")
    // ライセンス表記 (設定画面の「ライセンス」)。copyNotices が作る
    sourceSets["main"].assets.srcDirs(layout.buildDirectory.dir("generated/notices"))
    // libmozc_helper.so を実行するには、ネイティブライブラリが APK から展開されている必要がある
    packaging { jniLibs { useLegacyPackaging = true } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// Mozc (BSD-3-Clause) と .NET ランタイム (MIT) は、配布するバイナリにライセンス表記を付ける必要がある。
// ファイル名は settings/Notices.kt の FILES と合わせる。build/notices は mozc/build.sh と core-native/build.sh が作る。
val copyNotices by tasks.registering(Copy::class) {
    into(layout.buildDirectory.dir("generated/notices/notices"))
    from(rootProject.file("THIRD-PARTY-NOTICES.md"))
    from(rootProject.file("LICENSE"))
    from(rootProject.file("upstream/THIRD-PARTY-NOTICES.md")) { rename { "UPSTREAM-THIRD-PARTY-NOTICES.md" } }
    from(rootProject.file("licenses")) { include("KOTLIN-NOTICES.md", "APACHE-2.0.txt", "BOOST-1.0.txt") }
    from(rootProject.file("licenses/native")) {
        include("MOZC-LICENSE.txt", "MOZC-CREDITS.html", "DOTNET-LICENSE.txt", "DOTNET-THIRD-PARTY-NOTICES.txt")
    }
}
tasks.named("preBuild") { dependsOn(copyNotices) }
// Release builds must not silently omit notices or add an unreviewed runtime library.
val requiredReleaseNoticeSources = mapOf(
    "THIRD-PARTY-NOTICES.md" to "THIRD-PARTY-NOTICES.md",
    "LICENSE" to "LICENSE",
    "UPSTREAM-THIRD-PARTY-NOTICES.md" to "upstream/THIRD-PARTY-NOTICES.md",
    "MOZC-LICENSE.txt" to "licenses/native/MOZC-LICENSE.txt",
    "MOZC-CREDITS.html" to "licenses/native/MOZC-CREDITS.html",
    "DOTNET-LICENSE.txt" to "licenses/native/DOTNET-LICENSE.txt",
    "DOTNET-THIRD-PARTY-NOTICES.txt" to "licenses/native/DOTNET-THIRD-PARTY-NOTICES.txt",
    "KOTLIN-NOTICES.md" to "licenses/KOTLIN-NOTICES.md",
    "APACHE-2.0.txt" to "licenses/APACHE-2.0.txt",
    "BOOST-1.0.txt" to "licenses/BOOST-1.0.txt",
)
val reviewedRuntimeModules = mapOf(
    "org.jetbrains.kotlin:kotlin-stdlib" to "2.1.0",
    "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm" to "1.7.3",
    "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm" to "1.7.3",
    "org.jetbrains:annotations" to "13.0",
)
val verifyReleaseNotices by tasks.registering {
    dependsOn(copyNotices)
    doLast {
        val missingSources = requiredReleaseNoticeSources.filter { (_, path) ->
            rootProject.file(path).let { !it.isFile || it.length() == 0L }
        }.keys
        check(missingSources.isEmpty()) {
            "Missing or empty release notice sources: ${missingSources.joinToString()}"
        }
        // A regenerated native notice must match the reviewed copy before a release.
        for (name in listOf(
            "MOZC-LICENSE.txt", "MOZC-CREDITS.html",
            "DOTNET-LICENSE.txt", "DOTNET-THIRD-PARTY-NOTICES.txt",
        )) {
            val generated = rootProject.file("build/notices/$name")
            if (generated.isFile) {
                check(generated.readBytes().contentEquals(rootProject.file("licenses/native/$name").readBytes())) {
                    "Regenerated $name differs from the reviewed notice in licenses/native"
                }
            }
        }
        val assets = layout.buildDirectory.dir("generated/notices/notices").get().asFile
        val missing = requiredReleaseNoticeSources.keys.filter { name ->
            assets.resolve(name).let { !it.isFile || it.length() == 0L }
        }
        check(missing.isEmpty()) { "Missing or empty release notices: ${missing.joinToString()}" }

        val runtimeModules = configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.associate { artifact ->
                "${artifact.moduleVersion.id.group}:${artifact.name}" to artifact.moduleVersion.id.version
            }
        val required = reviewedRuntimeModules.keys - "org.jetbrains:annotations"
        val absent = required - runtimeModules.keys
        val unreviewed = runtimeModules.keys - reviewedRuntimeModules.keys
        val mismatched = runtimeModules.filter { (module, version) ->
            reviewedRuntimeModules[module] != null && reviewedRuntimeModules[module] != version
        }
        check(absent.isEmpty() && unreviewed.isEmpty() && mismatched.isEmpty()) {
            "Review release runtime dependencies and notices. Missing: $absent; " +
                "unreviewed: $unreviewed; version changes: $mismatched; resolved: $runtimeModules"
        }
        println("Reviewed release runtime dependencies: $runtimeModules")
    }
}
tasks.matching { it.name == "preReleaseBuild" || it.name == "mergeReleaseAssets" }.configureEach { dependsOn(verifyReleaseNotices) }

// An unsigned or partially configured Release APK must never pass assembleRelease.
val verifyReleaseSigning by tasks.registering {
    doLast {
        val names = listOf("MELTYPE_KEYSTORE", "MELTYPE_KEYSTORE_PASSWORD", "MELTYPE_KEY_ALIAS", "MELTYPE_KEY_PASSWORD")
        val missing = names.filter { System.getenv(it).isNullOrBlank() }
        check(missing.isEmpty()) { "Release signing environment is incomplete: ${missing.joinToString()}" }
        val keyFile = file(System.getenv("MELTYPE_KEYSTORE"))
        check(keyFile.isFile) { "Release keystore file does not exist." }
        val root = rootProject.projectDir.canonicalFile.toPath()
        check(!keyFile.canonicalFile.toPath().startsWith(root)) {
            "Release keystore must be outside the public repository."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseSigning) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
