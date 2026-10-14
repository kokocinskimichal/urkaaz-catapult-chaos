plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":game-contracts"))
    implementation(project(":map-editor-domain"))
    testImplementation(kotlin("test"))
}
