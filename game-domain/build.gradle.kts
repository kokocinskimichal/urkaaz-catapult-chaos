plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":game-contracts"))
    testImplementation(kotlin("test"))
}
