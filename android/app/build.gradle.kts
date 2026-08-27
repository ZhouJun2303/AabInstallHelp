import java.security.KeyStore
import java.security.PrivateKey
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun readDesktopVersion(): String {
    val pkg = rootProject.file("../package.json")
    val text = pkg.readText()
    val match = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)
    return match?.groupValues?.get(1) ?: "1.0.0"
}

fun versionCodeOf(version: String): Int {
    val parts = version.split('.').map { part ->
        part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return major * 10000 + minor * 100 + patch
}

val appVersion = readDesktopVersion()

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    namespace = "com.fireantzhang.aabinstallhelp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fireantzhang.aabinstallhelp"
        minSdk = 26
        targetSdk = 34
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        buildConfigField("String", "GITHUB_REPO", "\"ZhouJun2303/AabInstallHelp\"")
        buildConfigField("String", "PROJECT_URL", "\"https://github.com/ZhouJun2303/AabInstallHelp\"")
        buildConfigField("String", "DEBUG_CERT_SHA256", "\"890EEC543F84511357829E78B69D859BCFE5ED4A65393FDF57BB84F396D1AF47\"")
    }

    signingConfigs {
        create("release") {
            val store = keystoreProps.getProperty("storeFile")
            if (!store.isNullOrBlank()) {
                storeFile = file(store)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("")
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
            pickFirsts += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/io.netty.versions.properties"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val prepareRuntimeAssets by tasks.registering {
    val destDir = layout.projectDirectory.dir("src/main/assets/runtime")
    val srcKeystore = rootProject.file("../assets_common/debug.keystore")
    inputs.file(srcKeystore)
    outputs.dir(destDir)
    doLast {
        val dest = destDir.asFile
        dest.mkdirs()
        dest.listFiles()?.forEach { it.delete() }

        val ks = KeyStore.getInstance("JKS")
        srcKeystore.inputStream().use { ks.load(it, "android".toCharArray()) }
        val key = ks.getKey("androiddebugkey", "android".toCharArray()) as? PrivateKey
            ?: error("debug.keystore 中没有 androiddebugkey 私钥")
        val cert = ks.getCertificate("androiddebugkey")
            ?: error("debug.keystore 中没有 androiddebugkey 证书")
        dest.resolve("debug-key.pk8").writeBytes(key.encoded)
        dest.resolve("debug-cert.der").writeBytes(cert.encoded)
    }
}

val prepareJniLibs by tasks.registering {
    val jniDir = layout.projectDirectory.dir("src/main/jniLibs")
    val armSrc = rootProject.file("runtime/aapt2-arm64-v8a")
    val x64Src = rootProject.file("runtime/aapt2-x86_64")
    inputs.files(armSrc, x64Src)
    outputs.dir(jniDir)
    doLast {
        copy {
            from(armSrc)
            into(jniDir.dir("arm64-v8a"))
            rename { "libaapt2.so" }
        }
        copy {
            from(x64Src)
            into(jniDir.dir("x86_64"))
            rename { "libaapt2.so" }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(prepareRuntimeAssets, prepareJniLibs)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.android.tools.build:bundletool:1.17.2")
    implementation("com.google.protobuf:protobuf-java:3.25.3")
    implementation("com.google.protobuf:protobuf-java-util:3.25.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
