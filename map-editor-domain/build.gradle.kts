plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":game-contracts"))
    implementation(project(":game-campaign"))
    implementation(project(":game-assets"))
    testImplementation(kotlin("test"))
}
