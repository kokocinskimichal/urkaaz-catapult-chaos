plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":game-contracts"))
    implementation(project(":game-domain"))
    testImplementation(kotlin("test"))
}
