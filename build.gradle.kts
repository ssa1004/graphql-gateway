// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
// 이 레포는 100% Kotlin — Java 소스 0. 그래서 Lombok / annotationProcessor 도 없다.
plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    id("org.springframework.boot") version "3.5.15" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // Kover — Kotlin 네이티브 커버리지. 루트에 적용해 5개 모듈을 한 리포트로 병합한다.
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

allprojects {
    group = "com.example.gateway"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

// 5개 모듈 커버리지를 루트로 병합 — `./gradlew koverHtmlReport` / `koverXmlReport` 가
// 모든 모듈을 합친 단일 리포트를 build/reports/kover 아래에 만든다.
dependencies {
    kover(project(":gateway-domain"))
    kover(project(":gateway-application"))
    kover(project(":gateway-adapter-in"))
    kover(project(":gateway-adapter-out"))
    kover(project(":gateway-bootstrap"))
}

kover {
    reports {
        // 병합 리포트 산출물.
        //   - XML: build/reports/kover/report.xml  — CI 의 커버리지 배지 생성용.
        //   - HTML: build/reports/kover/html/index.html — 사람이 보는 리포트.
        total {
            xml { onCheck = false }
            html { onCheck = false }
        }
        filters {
            excludes {
                // 측정해도 의미 없는 코드는 분모에서 뺀다 — 숫자를 부풀리지 않기 위함.
                //   - Spring Boot main 진입점(GatewayApplication).
                //   - stub 데모 데이터(StubData/StubAdapters) — demo 프로필 한정 가짜 데이터.
                classes(
                    "com.example.gateway.GatewayApplication",
                    "com.example.gateway.GatewayApplicationKt",
                    "com.example.gateway.adapter.out.stub.*",
                )
            }
        }
    }
}

subprojects {
    apply(plugin = "io.spring.dependency-management")

    // 모든 모듈에 Kover 를 적용해야 루트 병합 리포트가 각 모듈 커버리지를 수집한다.
    apply(plugin = "org.jetbrains.kotlinx.kover")

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.15")
        }
    }

    // 모든 모듈이 Kotlin — JVM 21 toolchain + null-safety strict 를 한 곳에서 강제.
    // kotlin 플러그인이 붙어야 java/test 컨피그가 생기므로 의존성 선언도 이 블록 안에서 한다.
    plugins.withId("org.jetbrains.kotlin.jvm") {
        the<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>().apply {
            jvmToolchain(21)
            compilerOptions {
                // JSR-305 (@Nullable 등) 어노테이션을 strict 로 해석 — downstream DTO null 안전.
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }

        dependencies {
            // Gradle 8+ 부터 launcher 가 transitively 안 끌려옴 → 명시.
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}
