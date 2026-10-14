plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":map-editor-domain"))
    implementation(project(":map-editor-format"))
    implementation(project(":map-editor-rendering"))
    implementation(project(":map-editor-assets"))
    implementation(project(":game-assets"))
    implementation(project(":game-campaign"))
    implementation(project(":game-contracts"))
}

application {
    mainClass.set("com.urkaaaz.mapeditor.desktop.MapEditorMainKt")
}
