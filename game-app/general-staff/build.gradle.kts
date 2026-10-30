plugins {
    id("triplea-java-library")
    id("application")
}

application {
    mainClass.set("org.triplea.generalstaff.api.Cli")
}

dependencies {
    implementation(project(":game-core"))
    implementation(project(":java-extras"))

    testImplementation(testFixtures(project(":game-core")))
}

tasks.named<Test>("test") {
    // The battle calculator deserializes one copy of the Global 1940 game data per worker thread.
    maxHeapSize = "2G"
}
