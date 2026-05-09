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
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
