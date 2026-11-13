import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "dev.autotests"
version = "1.19.0"

// Machine-specific settings (the IDE to build against) live in local.properties, which is not committed:
// copy local.properties.example to local.properties and set ide.path.
val localSettings = Properties().apply {
    file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val idePath: String = localSettings.getProperty("ide.path")
    ?: throw GradleException("Set ide.path in local.properties (copy local.properties.example): the IDE to build against")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against the locally installed PyCharm: no SDK download
        local(idePath)
        bundledPlugin("PythonCore")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // the platform test classpath (IDE testFramework.jar) needs JUnit 4 classes even for plain JUnit 5 tests
    testRuntimeOnly("junit:junit:4.13.2")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false
    pluginVerification {
        ides {
            local(file(idePath))
            ide(IntelliJPlatformType.PyCharmCommunity, "2024.3.6.1")
            ide(IntelliJPlatformType.PyCharmProfessional, "2026.2.3")
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
}
