// Outbound adapter — 9개 downstream service 의 REST 를 호출하는 WebClient 어댑터.
// Resilience4j 로 서킷 브레이커 / 재시도 / 타임아웃을 입히고, Caffeine 으로 resolver 캐시.
// Application 의 downstream port 인터페이스를 구현한다.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":gateway-domain"))
    implementation(project(":gateway-application"))

    // WebClient (non-blocking) — downstream 9 service REST 호출.
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Resilience4j — CB + retry + timelimiter. reactor 연동 모듈로 WebClient 호출을 감싼다.
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.4.0")
    implementation("io.github.resilience4j:resilience4j-reactor:2.4.0")
    implementation("io.github.resilience4j:resilience4j-kotlin:2.4.0")

    // Caffeine — resolver 캐시 (downstream 응답 단기 캐싱 + stampede 회피).
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    // WireMock — downstream 9 service 가 안 떠 있어도 어댑터를 단위 검증.
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
}
