plugins { kotlin("jvm") version "2.0.20"; application }
repositories { mavenCentral() }
dependencies { implementation("io.ktor:ktor-server-core-jvm:2.3.12"); implementation("io.ktor:ktor-server-netty-jvm:2.3.12"); testImplementation(kotlin("test")) }
application { mainClass.set("{{PACKAGE_NAME}}.ApplicationKt") }
