plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "dev.autotests"
version = "1.11.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Собираем против установленного PyCharm — SDK не скачивается
        local("/Applications/PyCharm CE.app")
        bundledPlugin("PythonCore")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "231"
            untilBuild = provider { null }
        }
    }
}
