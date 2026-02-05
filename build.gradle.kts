plugins {
    java
    id("io.quarkus")
    id("build.buf") version "0.11.0"
}

// Add a task dependency for compilation
tasks.named("compileJava").configure { dependsOn("bufGenerate") }

// Add the generated code to the main source set
sourceSets["main"].java { srcDir(layout.buildDirectory.dir("bufbuild/generated")) }

// Custom configuration for protoc plugins
val protocPlugins by configurations.creating

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(platform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-rest")
    implementation("javax.annotation:javax.annotation-api")
    implementation("build.buf:protovalidate:1.1.0")
    implementation("com.google.protobuf:protobuf-java:4.33.5")
    implementation("com.google.protobuf:protobuf-java-util:4.33.5")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")

    // Quarkus gRPC Mutiny protoc plugin
    protocPlugins("io.quarkus:quarkus-grpc-protoc-plugin:${quarkusPlatformVersion}")
}

configurations.all {
    resolutionStrategy {
        force("com.google.protobuf:protobuf-java:4.33.5")
        force("com.google.protobuf:protobuf-java-util:4.33.5")
    }
}

group = "simplewins"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Task to print the protoc plugin classpath for the wrapper script
tasks.register("printProtocPluginClasspath") {
    doLast {
        println(configurations["protocPlugins"].asPath)
    }
}
