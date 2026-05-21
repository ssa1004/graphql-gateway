// Application — resolver use case + downstream port 인터페이스.
// 도메인만 의존한다. Spring 의존성은 stereotype 어노테이션과 coroutine 한정.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":gateway-domain"))

    // @Service / @Component 등 stereotype 어노테이션만 사용 (web / data 스타터는 끌어오지 않음).
    implementation("org.springframework:spring-context")

    // suspend use case — downstream 호출이 모두 non-blocking.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // Reactor Context <-> coroutine 브리지 — TokenRelay 가 요청 컨텍스트에서 토큰을 읽는다.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}
