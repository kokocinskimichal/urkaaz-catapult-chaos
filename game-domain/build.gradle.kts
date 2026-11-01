plugins {
    kotlin("jvm")
    id("java-test-fixtures")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":game-contracts"))
    testImplementation(kotlin("test"))
    testFixturesImplementation(project(":game-contracts"))
}
