plugins {
    java
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.12.0"
}

group = "com.solarmc"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://plugins.gradle.org/m2/")
    maven(url = "https://maven.quiltmc.org/")
    maven(url = "https://maven.fabricmc.net/")
    maven(url = "https://jitpack.io")
    mavenCentral()
}

dependencies {
    // Mapping
    implementation("cuchaz", "enigma-swing", "0.27.3")

    // Misc Libraries
    implementation("javax.servlet", "javax.servlet-api", "3.0.1")
    implementation("com.fasterxml.jackson.core", "jackson-databind", "2.12.3")
    implementation("com.google.guava", "guava", "30.1.1-jre")
    implementation("org.jetbrains", "annotations", "20.1.0")

    // Remapping & Bytecode Modification
    implementation("net.fabricmc", "tiny-remapper", "0.3.2")
    implementation("net.fabricmc", "lorenz-tiny", "3.0.0")
    implementation("org.ow2.asm", "asm", "9.1")
    implementation("org.ow2.asm", "asm-commons", "9.1")

    // Patching
    implementation("io.sigpipe", "jbsdiff", "1.0")
    implementation("com.github.Solar-MC", "javaxdelta", "efb21b70b7")

    // IO
    implementation("org.zeroturnaround", "zt-zip", "1.14")
    implementation("com.google.code.gson", "gson", "2.8.6")
    implementation("commons-io", "commons-io", "2.8.0")
}

gradlePlugin {
    plugins {
        create("Eclipse") {
            id = "com.solarmc.eclipse"
            implementationClass = "com.solarmc.eclipse.EclipsePlugin"
        }
    }
}