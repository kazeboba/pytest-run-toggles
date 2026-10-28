import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "dev.autotests"
version = "1.18.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against the locally installed PyCharm: no SDK download
        local("/Applications/PyCharm CE.app")
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
            local(file("/Applications/PyCharm CE.app"))
            ide(IntelliJPlatformType.PyCharmCommunity, "2024.3.6.1")
            ide(IntelliJPlatformType.PyCharmProfessional, "2026.2.3")
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "231"
            untilBuild = provider { null }
        }
    }
}
