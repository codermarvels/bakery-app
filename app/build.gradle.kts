import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase settings, first match wins: local.properties (your computer), then environment
// variables (CI), then the public project defaults in gradle.properties.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun buildSetting(propertyKey: String, environmentKey: String): String =
    listOf(
        localProperties.getProperty(propertyKey),
        System.getenv(environmentKey),
        providers.gradleProperty("vsj.$propertyKey").orNull,
    ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

// Release signing (Google Play upload key). The keystore file and passwords NEVER go in Git:
// they come from local.properties (release.storeFile=…, release.storePassword=…,
// release.keyAlias=…, release.keyPassword=…) or from CI secrets. See docs/RELEASE.md.
val releaseStoreFile = buildSetting("release.storeFile", "VSJ_RELEASE_STORE_FILE")

android {
    namespace = "com.vsjbakery.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vsjbakery.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "SUPABASE_URL", "\"${buildSetting("supabase.url", "SUPABASE_URL")}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${buildSetting("supabase.publishableKey", "SUPABASE_PUBLISHABLE_KEY")}\"",
        )
        // Firebase identifiers for new-order alerts (public app identifiers, not secrets; the
        // secret service-account key lives only in Supabase). Empty = alerts switched off.
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${buildSetting("firebase.projectId", "FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${buildSetting("firebase.appId", "FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${buildSetting("firebase.apiKey", "FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${buildSetting("firebase.senderId", "FIREBASE_SENDER_ID")}\"")
        // Website with the privacy policy, terms and contact page (linked from the Account tab).
        buildConfigField("String", "POLICIES_URL", "\"${buildSetting("policies.url", "POLICIES_URL")}\"")
    }

    signingConfigs {
        if (releaseStoreFile.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = buildSetting("release.storePassword", "VSJ_RELEASE_STORE_PASSWORD")
                keyAlias = buildSetting("release.keyAlias", "VSJ_RELEASE_KEY_ALIAS")
                keyPassword = buildSetting("release.keyPassword", "VSJ_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Without the upload key, the release build is signed with the debug key so it can
            // be installed on an emulator for testing. Google Play rejects such builds.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.storage)
    implementation(libs.razorpay.checkout)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
