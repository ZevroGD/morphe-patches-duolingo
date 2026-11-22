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
