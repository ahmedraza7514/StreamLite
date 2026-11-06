plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.streamlite.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.streamlite.tv"
        minSdk = 23
        targetSdk = 35
        // 2026-11-04: v2.5 — Batch 5 (pts 12-14): AUDIO/SUBS dialogs toggle on
        // re-click + sit above the bar; Volume/Brightness in a horizontal ROW;
        // WHITE 3dp border + scale on channel/movie/series GRID ITEMS.
        // Rule: bump versionCode + versionName on every build that ships changes.
        versionCode = 17
        versionName = "2.13.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material3:material3")
    // Material icons (white vector transport icons for the player bar)
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Compose for TV (D-pad friendly components)
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.tv:tv-foundation:1.0.0")

    // Media3 ExoPlayer (playback engine)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image loading (channel logos, posters)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Login persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
