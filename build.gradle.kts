import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "org.ncraft.grs"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.liquibase:liquibase-core")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation(libs.htmx.spring.boot.thymeleaf)
    implementation(libs.java.uuid.generator)
    implementation(libs.springdoc.openapi)
    implementation(libs.mapstruct)

    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")

    // CRITICAL: Lombok processor MUST be declared before MapStruct
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor(libs.lombok.mapstruct.binding)
    annotationProcessor(libs.mapstruct.processor)

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    buildInfo {
        properties {
            additional.set(
                mapOf(
                    "buildNumber" to (System.getenv("BUILD_NUMBER") ?: "local-dev")
                )
            )
        }
    }
}

tasks.withType<ProcessResources> {
    filesMatching(listOf("application.yml", "application.properties")) {
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "PROJECT_NAME" to project.name,
                "PROJECT_VERSION" to project.version.toString()
            )
        )
    }
}