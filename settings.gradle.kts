// Gradle 8+ Foojay 플러그인 — 시스템에 Java 21 미설치 시 toolchain 을 자동 다운로드.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "graphql-gateway"

include(
    "gateway-domain",
    "gateway-application",
    "gateway-adapter-in",
    "gateway-adapter-out",
    "gateway-bootstrap",
)
