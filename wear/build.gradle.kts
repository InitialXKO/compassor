import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val storeFilePath = keystoreProperties.getProperty("storeFile")
val storeFileObj = if (storeFilePath != null) {
    val moduleFile = file(storeFilePath)
    if (moduleFile.exists()) moduleFile else rootProject.file(storeFilePath)
} else null
val hasValidKeystore = keystorePropertiesFile.exists() && storeFileObj != null && storeFileObj.exists()

android {
    namespace = "com.growsnova.compassor.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.growsnova.compassor"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.6.8"
    }

    signingConfigs {
        if (hasValidKeystore) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = storeFileObj
                storePassword = keystoreProperties["storePassword"] as String
            }
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
            if (hasValidKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
