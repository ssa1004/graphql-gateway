// Spring Boot 진입점. main + 통합 config + application.yml + schema.graphqls.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":gateway-domain"))
    implementation(project(":gateway-application"))
    implementation(project(":gateway-adapter-in"))
    implementation(project(":gateway-adapter-out"))

    // Kotlin runtime — reflect 는 Spring proxy / @ConfigurationProperties 분석에 필요.
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Bootstrap 자체에서 사용하는 starter.
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Resilience4j — application.yml 의 resilience4j.* 바인딩 + actuator 노출.
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.4.0")

    // 관측 — actuator + Prometheus + OpenTelemetry trace.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    // WireMock — @SpringBootTest 에서 downstream 9 service 를 mock.
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    archiveClassifier.set("boot")
}

// 통합 테스트 / 외부 도구가 GatewayApplication 클래스를 import 할 수 있도록 plain jar 도 활성화.
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
