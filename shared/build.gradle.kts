import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/omninodeAppVersion/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/fcmCredentials/kotlin"))
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.adaptive)
            implementation(libs.compose.material3.adaptive.layout)
            implementation(libs.compose.material3.adaptive.navigation)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)

            implementation(libs.room3.runtime)
            implementation(libs.sqlite.bundled)

            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.qrose)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            implementation(libs.kotlinx.coroutines.play.services)
        }

        val desktopMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/desktopCloud/kotlin"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jna)
                implementation(libs.jmdns)
            }
        }
    }
}

android {
    namespace = "com.omninode.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        val webClientId = providers.gradleProperty("omninode.google.web.client.id").orElse("").get()
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("kspAndroid", libs.room3.compiler)
    add("kspDesktop", libs.room3.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

val generateDesktopCloudConfig = tasks.register("generateDesktopCloudConfig") {
    val outDir = layout.buildDirectory.dir("generated/desktopCloud/kotlin")
    val webClientSecret = providers.gradleProperty("omninode.google.web.client.secret").orElse("")
    inputs.property("webClientSecret", webClientSecret)
    outputs.dir(outDir)
    doLast {
        val secret = webClientSecret.get()
        val escapedSecret = secret.replace("\\", "\\\\").replace("\"", "\\\"")
        val dir = outDir.get().asFile.resolve("com/omninode/cloud")
        dir.mkdirs()
        dir.resolve("GeneratedDesktopCloudConfig.kt").writeText(
            """
            |package com.omninode.cloud
            |
            |/** Generated from gradle.properties — do not edit. */
            |internal object GeneratedDesktopCloudConfig {
            |    const val WEB_CLIENT_SECRET = "$escapedSecret"
            |}
            """.trimMargin()
        )
    }
}

fun escapeKotlinString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n")

fun resolveFirebaseServiceAccountJson(rootDir: java.io.File, explicitPath: String): java.io.File? {
    if (explicitPath.isNotBlank()) {
        val configured = rootDir.resolve(explicitPath)
        if (configured.isFile) return configured
    }
    return rootDir.listFiles()
        ?.firstOrNull { file -> file.isFile && file.name.contains("firebase-adminsdk") && file.name.endsWith(".json") }
}

val generateFcmCredentials = tasks.register("generateFcmCredentials") {
    val outDir = layout.buildDirectory.dir("generated/fcmCredentials/kotlin")
    val jsonPath = providers.gradleProperty("omninode.firebase.service.account.json").orElse("")
    inputs.property("jsonPath", jsonPath)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("com/omninode/cloud")
        dir.mkdirs()
        val jsonFile = resolveFirebaseServiceAccountJson(rootProject.projectDir, jsonPath.get())
        if (jsonFile == null) {
            dir.resolve("GeneratedFcmCredentials.kt").writeText(
                """
                |package com.omninode.cloud
                |
                |/** Generated at build time — credentials empty when service account JSON is missing. */
                |internal object GeneratedFcmCredentials {
                |    const val PROJECT_ID = ""
                |    const val CLIENT_EMAIL = ""
                |    const val PRIVATE_KEY_PEM = ""
                |}
                |
                |internal fun GeneratedFcmCredentials.toConfig(): FcmServiceAccountConfig? = null
                """.trimMargin()
            )
            return@doLast
        }
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parseText(jsonFile.readText()) as Map<String, Any?>
        val projectId = json["project_id"]?.toString().orEmpty()
        val clientEmail = json["client_email"]?.toString().orEmpty()
        val privateKey = json["private_key"]?.toString().orEmpty()
        dir.resolve("GeneratedFcmCredentials.kt").writeText(
            """
            |package com.omninode.cloud
            |
            |/** Generated from local Firebase Admin SDK JSON — never commit this file. */
            |internal object GeneratedFcmCredentials {
            |    const val PROJECT_ID = "${escapeKotlinString(projectId)}"
            |    const val CLIENT_EMAIL = "${escapeKotlinString(clientEmail)}"
            |    const val PRIVATE_KEY_PEM = "${escapeKotlinString(privateKey)}"
            |}
            |
            |internal fun GeneratedFcmCredentials.toConfig(): FcmServiceAccountConfig? {
            |    if (PROJECT_ID.isBlank() || CLIENT_EMAIL.isBlank() || PRIVATE_KEY_PEM.isBlank()) return null
            |    return FcmServiceAccountConfig(
            |        projectId = PROJECT_ID,
            |        clientEmail = CLIENT_EMAIL,
            |        privateKeyPem = PRIVATE_KEY_PEM
            |    )
            |}
            """.trimMargin()
        )
    }
}

val generateOmniNodeAppVersion = tasks.register("generateOmniNodeAppVersion") {
    val outDir = layout.buildDirectory.dir("generated/omninodeAppVersion/kotlin")
    val versionName = providers.gradleProperty("omninode.version.name")
    val versionCode = providers.gradleProperty("omninode.version.code")
    inputs.property("versionName", versionName)
    inputs.property("versionCode", versionCode)
    outputs.dir(outDir)
    doLast {
        val name = versionName.get()
        val code = versionCode.get()
        val dir = outDir.get().asFile.resolve("com/omninode/update")
        dir.mkdirs()
        dir.resolve("GeneratedAppVersion.kt").writeText(
            """
            |package com.omninode.update
            |
            |/**
            | * Generated from gradle.properties — do not edit.
            | *   omninode.version.name → [NAME]
            | *   omninode.version.code → [CODE]
            | */
            |internal object GeneratedAppVersion {
            |    const val NAME = "$name"
            |    const val CODE = $code
            |}
            """.trimMargin()
        )
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateOmniNodeAppVersion, generateDesktopCloudConfig, generateFcmCredentials)
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn(generateOmniNodeAppVersion, generateDesktopCloudConfig, generateFcmCredentials)
}

