plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2025.12")
    implementation("com.aayushatharva.brotli4j:brotli4j:1.20.0")
    implementation("com.github.luben:zstd-jni:1.5.7-6")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.11")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.18.2")
    runtimeOnly("com.aayushatharva.brotli4j:native-osx-aarch64:1.20.0")
    runtimeOnly("com.aayushatharva.brotli4j:native-osx-x86_64:1.20.0")
    runtimeOnly("com.aayushatharva.brotli4j:native-linux-x86_64:1.20.0")
    runtimeOnly("com.aayushatharva.brotli4j:native-linux-aarch64:1.20.0")
    runtimeOnly("com.aayushatharva.brotli4j:native-windows-x86_64:1.20.0")
    runtimeOnly("com.aayushatharva.brotli4j:native-windows-aarch64:1.20.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.encoding = "UTF-8"
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().filter { it.isDirectory })
    from(configurations.runtimeClasspath.get().filterNot { it.isDirectory }.map { zipTree(it) })
}