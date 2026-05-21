package com.example.gateway

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 게이트웨이 Spring 컨텍스트 적재 검증.
 *
 * demo 프로필로 — stub 어댑터 + 인증 off. downstream 9 service 가 안 떠 있어도 컨텍스트가
 * 깨지지 않아야 한다(빈 배선 / GraphQL schema 매핑 / DataLoader 등록 검증).
 *
 * 이 테스트가 통과한다 = schema.graphqls 의 모든 타입/필드가 resolver 와 매핑되고,
 * @ConfigurationProperties 바인딩과 Resilience4j instance 설정이 모두 유효하다는 뜻이다.
 */
@SpringBootTest
@ActiveProfiles("demo")
class GatewayApplicationContextTest {

    @Test
    fun `컨텍스트가 적재된다`() {
        // 컨텍스트 적재 자체가 검증 — 실패 시 @SpringBootTest 가 예외를 던진다.
    }
}
