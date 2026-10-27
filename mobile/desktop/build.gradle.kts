import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The Linux desktop app: the shared screens in a Compose Desktop window.
// Same backend and token as the phone, from the same gitignored mobile/.env.
val envProps = Properties().apply {
    rootProject.file(".env").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val apiBaseUrl = envProps.getProperty("API_BASE_URL", "http://10.7.0.2:3181/")
val apiToken = envProps.getProperty("API_TOKEN", "")

val generateConfig by tasks.registering {
    val out = layout.buildDirectory.dir("generated/config")
    inputs.property("url", apiBaseUrl)
    inputs.property("token", apiToken)
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/automatelinux/mywhatsapp/DesktopConfig.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            "package com.automatelinux.mywhatsapp\n\n" +
                "internal object DesktopConfig {\n" +
                "    const val API_BASE_URL = \"$apiBaseUrl\"\n" +
                "    const val API_TOKEN = \"$apiToken\"\n" +
                "}\n",
        )
    }
}

kotlin {
    jvm()
    sourceSets {
        jvmMain {
            kotlin.srcDir(generateConfig)
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.multiplatform.settings)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.automatelinux.mywhatsapp.MainKt"
        nativeDistributions {
            packageName = "MyWhatsApp"
            packageVersion = "1.0.0"
            // java.util.prefs is where the desktop keeps seen/filters/alerts state.
            modules("java.prefs")
        }
    }
}
