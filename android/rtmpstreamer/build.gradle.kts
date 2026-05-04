plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group = "com.github.gitlad11"
version = "0.1.0"

android {
    namespace = "com.gitlad.rtmpstreamer"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.7")
    implementation("com.github.pedroSG94.RootEncoder:rtmp:2.6.7")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = project.group.toString()
                artifactId = "rtmpstreamer"
                version = project.version.toString()

                pom {
                    name.set("RTMP Streamer")
                    description.set("Android RTMP streaming library with camera, screen sharing, preview, raw frames, and chunked frame capture.")
                    url.set("https://github.com/gitlad11/flutter_rtmp_screensharing")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("gitlad11")
                            name.set("gitlad11")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/gitlad11/flutter_rtmp_screensharing.git")
                        developerConnection.set("scm:git:ssh://github.com/gitlad11/flutter_rtmp_screensharing.git")
                        url.set("https://github.com/gitlad11/flutter_rtmp_screensharing")
                    }
                }
            }
        }
    }
}
