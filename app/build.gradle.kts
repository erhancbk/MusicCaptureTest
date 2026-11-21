plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.musiccapture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.erhancbk.sestest"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Native-code-free LAME 3.100 Java port; encodes genuine MPEG Layer III files.
dependencies {
    implementation("io.vacco.jlame:jlame:3.100.2")
}
