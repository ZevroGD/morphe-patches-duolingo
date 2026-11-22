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

    // Do not put the module's compiled patch classes on the test runtime classpath.
    // Otherwise URLClassLoader's parent-first lookup could load the patch from build/classes
    // instead of from the .mpp being tested.
    classpath = files(sourceSets.test.get().output, configurations.testRuntimeClasspath)
}
