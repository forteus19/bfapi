plugins {
    java
}

group = "dev.vuis"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-api:2.25.2")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.2")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.2")

    implementation("io.netty:netty-all:4.2.7.Final")
    implementation("com.google.code.gson:gson:2.13.2")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
}

tasks.test {
    useJUnitPlatform()
}
