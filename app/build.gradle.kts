import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Pull the Maps API key out of local.properties (which is .gitignored), with
// a graceful empty fallback so a fresh clone still builds even before the key
// is set up. Without a real key the map screen will load but tiles will be
// blank — that's intentional and signals "set MAPS_API_KEY in local.properties".
val mapsApiKey: String = run {
    val props = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { props.load(it) }
    }
    props.getProperty("MAPS_API_KEY", "")
}

// Upload-keystore credentials for signing the AAB you publish to Google Play.
// Read from keystore.properties (gitignored — see keystore.properties.template).
// When the file is absent (fresh clones, CI) the release build falls back to
// debug signing below so assembleRelease/bundleRelease still produces an
// artifact that exercises R8 + release lint. That fallback build is NOT
// distributable: Play rejects debug-signed uploads.
val keystoreProps: Properties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseKeystoreFile: File? = keystoreProps.getProperty("storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

// --- Git info baked into BuildConfig for the in-app About card. providers.exec
// is configuration-cache safe and only re-runs when the underlying git output
// actually changes. isIgnoreExitValue covers the no-.git case (non-zero exit,
// blank stdout → "unknown" via the maps below); a missing git *binary* throws
// at provider evaluation instead — Provider.orElse can't catch that, so the
// .get() call sites wrap in runCatching to honor the same "unknown" fallback.
fun gitOutput(vararg command: String): Provider<String> =
    providers.exec {
        commandLine(*command)
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }

val gitSha: Provider<String> = gitOutput("git", "rev-parse", "--short", "HEAD")
    .map { it.ifBlank { "unknown" } }
    .orElse("unknown")
val gitDirty: Provider<String> = gitOutput("git", "status", "--porcelain")
    .map { if (it.isBlank()) "" else "-dirty" }
    .orElse("")
val gitCommitDate: Provider<String> = gitOutput("git", "log", "-1", "--format=%cI")
    .map { it.ifBlank { "unknown" } }
    .orElse("unknown")
val gitDescribe: Provider<String> = gitSha.zip(gitDirty) { sha, dirty -> sha + dirty }

// versionCode must strictly increase on every Play upload or the upload is
// rejected. Derive it from the git commit count so each build off a new commit
// gets a unique, monotonically increasing code with no manual bookkeeping.
// Override with -PevsctVersionCode=NN to reproduce a specific past upload.
// Falls back to 1 outside a git checkout (fresh tarball, no git binary).
val gitCommitCount: Provider<String> = gitOutput("git", "rev-list", "--count", "HEAD")
    .map { it.ifBlank { "1" } }
    .orElse("1")
val resolvedVersionCode: Int =
    (project.findProperty("evsctVersionCode") as String?)?.toIntOrNull()
        ?: runCatching { gitCommitCount.get().toInt() }.getOrDefault(1)

android {
    namespace = "com.evsct.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.evsct.app"
        minSdk = 30
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = "0.1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Surface the current commit in the About card. .get() at config
        // time is fine — Gradle's configuration cache replays the captured
        // strings on subsequent invocations and only re-runs the git execs
        // when the underlying inputs actually change. runCatching guards
        // the git-binary-missing case, where provider evaluation throws.
        val gitShaValue = runCatching { gitDescribe.get() }.getOrDefault("unknown")
        val gitCommitDateValue = runCatching { gitCommitDate.get() }.getOrDefault("unknown")
        buildConfigField("String", "GIT_SHA", "\"$gitShaValue\"")
        buildConfigField("String", "GIT_COMMIT_DATE", "\"$gitCommitDateValue\"")
    }

    signingConfigs {
        // Only declared when keystore.properties points at a real keystore; the
        // release buildType below picks this up when present and otherwise falls
        // back to debug signing so CI / fresh clones still build.
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Sign with the real upload keystore when keystore.properties is
            // present (see keystore.properties.template), producing the AAB you
            // upload to Play. Without it, fall back to the debug keystore so a
            // release build is still installable locally (./gradlew
            // installRelease) and CI's assembleRelease/bundleRelease still runs
            // R8 + release lint. A debug-signed build is NOT distributable —
            // Play rejects it.
            signingConfig = if (releaseKeystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // AGP 9 ships this off by default; re-enable so we can emit
        // GIT_SHA / GIT_COMMIT_DATE for the in-app About card.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt"
            )
        }
    }
}

// AGP-9's BaseAppModuleExtension.kotlinOptions is deprecated; configure
// Kotlin via the Kotlin plugin's own compilerOptions DSL instead. The
// -Xannotation-default-target flag opts the project into Kotlin 2.x's
// upcoming behaviour where constructor-parameter annotations also land on
// the backing property, so Hilt @Inject sites don't each need @param:
// (KT-73255).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)

    implementation(libs.coil.compose)

    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
