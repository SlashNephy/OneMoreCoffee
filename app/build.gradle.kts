import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mapsplatform.secrets)
}

fun loadRootProperties(path: String): Properties {
    val properties = Properties()
    val file = rootProject.file(path)

    if (file.isFile) {
        file.inputStream().use(properties::load)
    }

    return properties
}

fun Properties.nonBlankProperty(name: String): String? = getProperty(name)?.takeIf { it.isNotBlank() }

fun String.asBuildConfigStringLiteral(): String = buildString {
    append('"')

    for (character in this@asBuildConfigStringLiteral) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }

    append('"')
}

val mapsApiKey = loadRootProperties("secrets.properties").nonBlankProperty("MAPS_API_KEY")
    ?: loadRootProperties("secrets.defaults.properties").nonBlankProperty("MAPS_API_KEY")
    ?: "DEFAULT_API_KEY"

android {
    namespace = "blue.starry.onemorecoffee"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "blue.starry.onemorecoffee"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", mapsApiKey.asBuildConfigStringLiteral())
    }

    signingConfigs {
        create("default") {
            val keystoreProperties = Properties().apply {
                rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
            }

            storeFile = keystoreProperties.getProperty("android_keystore_path")?.let { file(it) }
            storePassword = keystoreProperties.getProperty("android_keystore_password")
            keyAlias = keystoreProperties.getProperty("android_keystore_alias")
            keyPassword = keystoreProperties.getProperty("android_keystore_alias_password")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("default")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = rootProject.relativePath("secrets.defaults.properties")
    ignoreList.add("MAPS_API_KEY")
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(projects.feature.map)
    implementation(projects.feature.list)
    implementation(projects.feature.stats)
    implementation(projects.feature.settings)
    implementation(projects.feature.import)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
