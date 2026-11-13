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
        // 2026-11-12: v2.14.0 — MultiView fix (Ahmed Bhai ka hukum):
        // har screen ki video TextureView par (charoon display hon)
        // aur 4-screen ke liye halka player (480p cap + chhota
        // buffer) taake box par tez aur smooth chale. Awaz ka rule
        // waisa ka waisa hai (ek waqt me ek screen). Baqi app mein
        // koi changing nahi.
        // 2026-11-13: v2.14.1 — sirf MultiView ki awaz ki switching
        // theek ki: volume ke sath AUDIO TRACK bhi on/off hota hai
        // (jo screen band-awaz se shuru hoti thi, us ki awaz khulti
        // hi nahi thi). Baqi sab v2.14.0 jesa hi hai.
        // 2026-11-13: v2.16.0 — Ahmed Bhai ke 13-11 wale notes par
        // SAB points: (1) card logos reference walay (TV set + LIVE
        // pill title, popcorn balti, kala clapper) aur chhoti
        // unchai par cards/panel khud sikor jate hain — koi hissa
        // kata nahi. (2) Neeche wale naam/count chhote. (3)
        // Dashboard patti patli. (4) 4-Screen awaz: jo screen
        // audible banti hai us ka player stop+prepare se taza
        // chalta hai (RIGHT column khamosh rehta tha). (5) Splash
        // se pehle wala poster HATA diya — direct video; splash
        // video halki kar di (8MB -> ~1MB, 10s -> 5s). (6) Images:
        // Coil disk cache 100MB -> 1GB aur sync par Live logos
        // pehle/priority se preload — pehle posters ki bheer Live
        // logos ko cache se nikal deti thi.
        versionCode = 21
        versionName = "2.16.0"
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
