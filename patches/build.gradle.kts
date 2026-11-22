group = "app.zevro"
version = providers.gradleProperty("version").orElse("0.0.0").get()

patches {
    about {
        name = "Zevro Patches"
        description = "Custom patches for Duolingo."
        source = "https://github.com/ZevroGD/morphe-patches-duolingo"
        author = "Zevro"
        contact = "https://github.com/ZevroGD"
        website = "https://github.com/ZevroGD/morphe-patches-duolingo"
        license = "GPLv3"
    }
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()

    // Keep compiled patch classes off the test runtime classpath so the loader
    // must discover the patch from the .mpp archive itself.
    classpath = files(sourceSets.test.get().output, configurations.testRuntimeClasspath)

    // Pass an absolute path instead of relying on Gradle's test working directory.
    systemProperty("mpp.bundle.dir", layout.buildDirectory.dir("libs").get().asFile.absolutePath)
}
