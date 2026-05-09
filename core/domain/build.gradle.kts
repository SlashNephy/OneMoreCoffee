plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "blue.starry.onemorecoffee.core.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
