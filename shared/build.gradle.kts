architectury {
    common("neoforge", "fabric")
}

// Global exclusions to prevent module conflicts
configurations.all {
    exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    exclude(group = "it.unimi.dsi", module = "fastutil")
    exclude(group = "com.nukkitx.fastutil")
    exclude(group = "commons-io", module = "commons-io")
    exclude(group = "com.google.code.gson", module = "gson")
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras)
    compileOnly(libs.geyser.api)
    compileOnly(libs.geyser.core) {
        exclude(group = "io.netty")
        exclude(group = "io.netty.incubator")
    }

    api(libs.pack.converter)

    implementation(libs.auto.service)
    annotationProcessor(libs.auto.service)

    // Only here to suppress "unknown enum constant EnvType.CLIENT" warnings.
    compileOnly(libs.fabric.loader)
}
