package com.example.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * 쿼리 complexity / depth 제한 검증 — GraphQL DoS 방어 (ADR-0008).
 *
 * 한계값을 일부러 낮게(`max-depth=3`, `max-complexity=8`) 잡아, 한계를 넘는 쿼리가 실행
 * 전에 거부되는지 확인한다. 한계 안의 정상 쿼리는 그대로 통과해야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@ActiveProfiles("demo")
@TestPropertySource(
    properties = [
        "gateway.query-guard.max-depth=3",
        "gateway.query-guard.max-complexity=8",
    ],
)
class QueryGuardTest {

    @Autowired
    lateinit var graphQlTester: HttpGraphQlTester

    @Test
    fun `depth 한계를 넘는 쿼리는 거부된다`() {
        // order -> user -> notifications -> id 로 깊이 4단계 — max-depth=3 을 넘는다.
        // 한계를 넘으면 graphql-java 가 실행 전에 AbortExecutionException 으로 거부한다.
        graphQlTester.document(
            """
            query {
              order(id: "o-1") {
                user {
                  notifications {
                    id
                  }
                }
              }
            }
            """.trimIndent(),
        )
            .execute()
            .errors()
            .satisfy { errors ->
                require(errors.isNotEmpty()) { "depth 한계를 넘으면 오류가 있어야 한다" }
            }
    }

    @Test
    fun `complexity 한계를 넘는 쿼리는 거부된다`() {
        // 필드를 넓게 펼쳐 complexity 비용을 8 이상으로 올린다 — 실행 전에 거부된다.
        graphQlTester.document(
            """
            query {
              order(id: "o-1") {
                id
                userId
                status
                totalAmount
                currency
                placedAt
                user { id email displayName status }
              }
            }
            """.trimIndent(),
        )
            .execute()
            .errors()
            .satisfy { errors ->
                require(errors.isNotEmpty()) { "complexity 한계를 넘으면 오류가 있어야 한다" }
            }
    }

    @Test
    fun `한계 안의 정상 쿼리는 통과한다`() {
        graphQlTester.document(
            """
            query { user(id: "u-1") { id email } }
            """.trimIndent(),
        )
            .execute()
            .errors().verify()
            .path("user.id").entity(String::class.java).isEqualTo("u-1")
    }
}
