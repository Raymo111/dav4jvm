import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

object Libs {
    // okhttp HTTP library
    const val okhttpVersion = "4.10.0"
}

repositories {
    mavenCentral()
}

group="com.github.Raymo111"
version="3.0.3"

plugins {
    kotlin("jvm") version "1.7.20"
    `maven-publish`

    id("org.jetbrains.dokka") version "1.7.20"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "dav4jvm"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            moduleName.set("dav4jvm")
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("https://github.com/Raymo111/dav4jvm/tree/main/src/main/kotlin/"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk7"))

    api("com.squareup.okhttp3:okhttp:${Libs.okhttpVersion}")
    implementation("org.apache.commons:commons-lang3:3.12.0")    // last version that doesn't require Java 8

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:${Libs.okhttpVersion}")
}

kotlin {
    sourceSets {
        main {
            dependencies {
                implementation("org.kobjects.ktxml:core:0.2.3")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}