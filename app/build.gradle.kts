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

// --- Git info baked into BuildConfig for the in-app About card. providers.exec
// is configuration-cache safe and only re-runs when the underlying git output
// actually changes. Each helper falls back to "unknown" when git isn't on the
// PATH or there's no .git directory (e.g. a fresh extract of a downloaded zip)
// so the build never fails just because version info is missing.
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

android {
    namespace = "com.evsct.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.evsct.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Surface the current commit in the About card. .get() at config
        // time is fine — Gradle's configuration cache replays the captured
        // strings on subsequent invocations and only re-runs the git execs
        // when the underlying inputs actually change.
        buildConfigField("String", "GIT_SHA", "\"${gitDescribe.get()}\"")
        buildConfigField("String", "GIT_COMMIT_DATE", "\"${gitCommitDate.get()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
