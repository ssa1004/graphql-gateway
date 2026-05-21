// Inbound adapter — GraphQL controller (@QueryMapping / @SchemaMapping / @BatchMapping)
// + DataLoader 등록 + JWT 인증 필터 + GraphQL error resolver + complexity/depth instrumentation.
// Application 의 UseCase 인터페이스만 호출한다.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":gateway-domain"))
    implementation(project(":gateway-application"))

    // Spring for GraphQL — @QueryMapping / @SchemaMapping / @BatchMapping / DataLoader.
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // JWT 검증 — auth-service 의 JWK Set 으로 Bearer 토큰을 resource server 모드로 검증.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Kotlin <-> Spring/Jackson.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Spring for GraphQL 이 suspend resolver 를 호출할 때 KotlinReflectionUtils 를 쓴다.
    // 이 유틸은 spring-data-commons 에 있어 명시 의존이 필요하다 (data-jpa 등은 안 씀).
    implementation("org.springframework.data:spring-data-commons")

    // 쿼리 complexity / depth 제한은 graphql-java 의 instrumentation 으로 구현.
    // spring-boot-starter-graphql 이 graphql-java 를 가져오지만, 직접 타입을 참조하므로 명시.
    implementation("com.graphql-java:graphql-java")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}
