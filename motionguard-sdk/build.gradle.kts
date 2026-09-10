plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "io.motionguard"
version = "0.1.0"

android {
    namespace = "motionguardsdk"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        singleVariant("release")
    }
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    api(libs.androidx.camera.core)
    api(libs.androidx.camera.lifecycle)
    api(libs.androidx.camera.view)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.mlkit.pose.detection)
    implementation(libs.tensorflow.lite)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "io.motionguard"
                artifactId = "motionguard-sdk"
                version = project.version.toString()
            }
        }
    }
}
