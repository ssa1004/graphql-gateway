// 순수 도메인 — GraphQL type 의 도메인 모델. Spring 의존성 0. (헥사고날 핵심)
// GraphQL 라이브러리 의존성도 0 — schema 매핑은 adapter-in 의 몫이고, 도메인은
// 그저 불변 데이터 클래스 모음이다.
plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.assertj:assertj-core")
}
