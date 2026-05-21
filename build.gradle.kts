// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
// 이 레포는 100% Kotlin — Java 소스 0. 그래서 Lombok / annotationProcessor 도 없다.
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "3.4.13" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.example.gateway"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "io.spring.dependency-management")

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.13")
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
