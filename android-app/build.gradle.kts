plugins {
    id("com.android.application")
}

android {
    namespace = "com.urkaaaz.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.urkaaaz.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(project(":game-application"))
    implementation(project(":game-ai"))
    implementation(project(":game-contracts"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}
