plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

tasks.register<JavaExec>("balanceTune") {
    group = "balance"
    description = "Random-searches balance parameters (-PbalanceArgs=\"candidates seeds dir levels randomSeed\")."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.urkaaaz.balance.BalanceTuner")
    args((findProperty("balanceArgs") as String?)?.split(" ")?.filter(String::isNotBlank).orEmpty())
}

tasks.register<JavaExec>("balanceRedHealth") {
    group = "balance"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.urkaaaz.balance.RedHealthScan")
    args((findProperty("balanceArgs") as String?)?.split(" ")?.filter(String::isNotBlank).orEmpty())
}

tasks.register<JavaExec>("balanceBaseline") {
    group = "balance"
    description = "Runs the Balance Simulation baseline (-PbalanceArgs=\"seeds dir levels\")."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.urkaaaz.balance.BalanceBaseline")
    args((findProperty("balanceArgs") as String?)?.split(" ")?.filter(String::isNotBlank).orEmpty())
}

dependencies {
    implementation(project(":game-contracts"))
    implementation(project(":game-campaign"))
    implementation(project(":game-domain"))
    implementation(project(":game-application"))
    implementation(project(":game-ai"))
    testImplementation(kotlin("test"))
}
