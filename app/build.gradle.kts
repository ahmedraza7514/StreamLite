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
        // 2026-11-13: v2.17.0 — Ahmed Bhai ka hukum: 4-Screen
        // (MultiView) ka code BACKUP wala wapas lagaya gaya hai —
        // v2.11 (build #236) wala, jo v2.13.2 me final tha.
        // MultiViewScreen.kt file bilkul jesi backup me thi wesi
        // hai, koi tabdeeli nahi (volume wala purana tareeqa;
        // v2.14.1 ki track switching aur v2.16.0 ka stop+prepare
        // dono khatam). Images ki asal kamzoriyan bhi durust:
        // cache ab filesDir me (Android use khud saaf nahi
        // karta), image timeouts 5s/8s se barha kar 10s/20s
        // (4 Mbps par slow logo sites haar jati theen). Baqi
        // sab v2.16.0 jesa hi hai (naya home design, tez
        // startup, 1GB image cache).
        // 2026-11-13: v2.18.0 — user ke 6 points (v2.17.0 test):
        // (1-2) awaz: PlayerFactory ke dono players me AAC ko
        // tarjeeh (AC3 pehle chun liya jata tha, bohat se phones
        // par decode nahi hota). (3) fail images ginna
        // (ThumbnailPreloader failed counter). (4) har opening
        // par auto-Sync khatam — saved session par seedha Home;
        // sync sirf naye login ya manual Update media par.
        // (5) Home QUICK ACCESS me alag "Update Thumbnails"
        // button. (6) splash 1.2s me aage barhta hai.
        // 2026-11-13: v2.19.0 — (1-2) AWAZ ka asal hal: FFmpeg
        // software decoder jama (media3-ffmpeg-decoder 1.5.0+1,
        // Media3 1.4.1 se 1.5.0) + PlayerFactory me extension
        // renderers ON — AC3/E-AC3/DTS ab software se decode
        // hon ge. (5) Update Thumbnails button khud-kafi: ab
        // Sync screen par nahi bhejta, khud catalog load karke
        // thumbnails chalata hai. (6) TASHEEH: splash ka time
        // purana hi (1.2s wali tabdeeli wapis); asal fix —
        // themes.xml ki window background me logo, taake app
        // khulte hi pehle frame par kuch nazar aye, splash se
        // pehle ka khaali intezar khatam.
        versionCode = 24
        versionName = "2.19.0"
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
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-common:1.5.0")
    // 2026-11-13 (v2.19.0): FFmpeg software audio decoder
    // (Jellyfin ki published library, Maven Central) — AC3 /
    // E-AC3 / DTS jese formats jo bohat se phones ke apne
    // decoder nahi chala sakte, ab software se decode hon ge.
    // Media3 ko 1.5.0 is liye kiya ke decoder isi version ke
    // sath jura hua hai.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image loading (channel logos, posters)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Login persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
