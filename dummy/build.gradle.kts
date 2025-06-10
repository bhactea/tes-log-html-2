plugins {
    id("com.android.application")
}

android {
    namespace = "id.harpa.logger.dummy"
    compileSdk = 33

    defaultConfig {
        applicationId = "id.harpa.logger.dummy"
        minSdk = 21
        targetSdk = 33
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":app"))
}
