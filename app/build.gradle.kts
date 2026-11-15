plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.aadith.mustre"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.aadith.mustre"
        minSdk = 34
        targetSdk = 37
        // Force devices to upgrade from builds made before LAN HTTP support was added.
        versionCode = 3
        versionName = "1.0.2"

//        buildConfigField("String", "BASE_URL", "\"https://rog-strix.tail68d919.ts.net\"")
//        buildConfigField("String", "BASE_URL", "\"http://192.168.18.67:3000\"")
//        buildConfigField("String", "BASE_URL", "\"http://10.117.28.160:3000\"")
        buildConfigField("String", "BASE_URL", "\"http://192.168.1.104:3000\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += setOf("en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/*.version"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.glide)
    implementation(libs.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
}
