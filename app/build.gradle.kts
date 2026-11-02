plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.sch.exam.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.sch.exambrowsersd"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
    }
}
dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
