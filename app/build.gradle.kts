import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

// 加载签名配置（如果存在）
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val storeFilePath = keystoreProperties.getProperty("storeFile")
val storeFileObj = if (storeFilePath != null) {
    val rootFile = rootProject.file(storeFilePath)
    if (rootFile.exists()) rootFile else file(storeFilePath)
} else null
val hasValidKeystore = keystorePropertiesFile.exists() && storeFileObj != null && storeFileObj.exists()

android {
    namespace = "com.growsnova.compassor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.growsnova.compassor"
        minSdk = 24
        targetSdk = 34
        versionCode = 10
        versionName = "1.6.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 签名配置
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
            // 如果签名配置存在且密钥库文件有效，则使用
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

    viewBinding {
        enable = true
    }
}

dependencies {
    implementation(project(":common"))

    // Android核心库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // 高德地图SDK
    implementation(libs.amap.map3d)
    implementation(libs.amap.search)

    // 协程支持
    implementation(libs.kotlinx.coroutines.android)

    // JSON序列化
    implementation(libs.gson)

    // Wear OS Play Services Wearable (DataLayer)
    implementation(libs.play.services.wearable)

    // RecyclerView
    implementation(libs.androidx.recyclerview)

    // ViewModel and LiveData KTX
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Glide
    implementation(libs.glide)
    kapt(libs.glideCompiler)

    // 测试库
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
