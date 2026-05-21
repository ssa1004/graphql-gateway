package com.example.gateway

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * downstream 9 service 를 WireMock 으로 세운 통합 테스트.
 *
 * stub 어댑터가 아니라 실제 WebClient 어댑터 경로를 검증한다 — WebClient HTTP 호출 /
 * JSON 역직렬화 / DTO->도메인 매핑 / Resilience4j / GraphQL 부분 실패까지 전부 탄다.
 *
 * 핵심 검증:
 *   - downstream JSON 을 실제로 받아 GraphQL 응답으로 변환 (정상 경로).
 *   - downstream 한 곳이 5xx 를 줘도 그 필드만 null 로 떨어지고 쿼리 전체는 산다 (ADR-0003).
 *
 * `gateway.downstream.*` base URL 을 WireMock 포트로 [DynamicPropertySource] 가 덮어쓴다.
 * downstream 9 service 가 실제로 안 떠 있어도 이 테스트는 자족적으로 돈다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DownstreamWireMockTest {

    @Autowired
    lateinit var graphQlTester: HttpGraphQlTester

    companion object {
        // 서버는 클래스 로딩 시점에 바로 띄운다 — @DynamicPropertySource 의 baseUrl 공급자가
        // 컨텍스트 적재 중 평가될 때 이미 포트가 열려 있어야 한다 (@BeforeAll 보다 먼저 필요).
        private val auth = WireMockServer(options().dynamicPort()).apply { start() }
        private val commerce = WireMockServer(options().dynamicPort()).apply { start() }
        private val billing = WireMockServer(options().dynamicPort()).apply { start() }

        @JvmStatic
        @AfterAll
        fun stopServers() {
            auth.stop()
            commerce.stop()
            billing.stop()
        }

        /**
         * WireMock 포트로 downstream base URL 을 주입한다. stub 모드는 끄고(=false) 실제
         * WebClient 어댑터를 쓰게 한다. 인증은 permit-all 로 꺼서 토큰 없이 호출한다.
         */
        @JvmStatic
        @DynamicPropertySource
        fun downstreamProps(registry: DynamicPropertyRegistry) {
            registry.add("gateway.downstream.stub") { "false" }
            registry.add("gateway.security.permit-all") { "true" }
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet." +
                    "OAuth2ResourceServerAutoConfiguration"
            }
            registry.add("management.tracing.enabled") { "false" }
            registry.add("gateway.downstream.auth.base-url", auth::baseUrl)
            registry.add("gateway.downstream.commerce.base-url", commerce::baseUrl)
            registry.add("gateway.downstream.billing.base-url", billing::baseUrl)
        }
    }

    @BeforeEach
    fun resetStubs() {
        auth.resetAll()
        commerce.resetAll()
        billing.resetAll()
    }

    @Test
    fun `downstream REST 응답이 GraphQL 응답으로 변환된다`() {
        auth.stubFor(
            get(urlPathEqualTo("/api/v1/users/u-1")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"id":"u-1","email":"u1@example.com","displayName":"홍길동",
                         "roles":["USER"],"status":"ACTIVE","createdAt":"2026-05-01T00:00:00Z"}
                        """.trimIndent(),
                    ),
            ),
        )

        graphQlTester.document("""query { user(id: "u-1") { id email displayName } }""")
            .execute()
            .path("user.email").entity(String::class.java).isEqualTo("u1@example.com")
            .path("user.displayName").entity(String::class.java).isEqualTo("홍길동")
    }

    @Test
    fun `downstream 한 곳이 5xx 여도 그 필드만 null 이고 쿼리는 산다 (부분 실패)`() {
        // commerce-ops 는 정상 — 주문은 내려준다.
        commerce.stubFor(
            get(urlPathEqualTo("/api/v1/orders/o-1")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"id":"o-1","userId":"u-1","status":"PAID","totalAmount":15000,
                         "currency":"KRW","placedAt":"2026-05-01T00:00:00Z","invoiceId":"inv-1"}
                        """.trimIndent(),
                    ),
            ),
        )
        // billing-platform 은 장애 — invoice 조인은 실패해야 한다.
        billing.stubFor(
            get(urlPathEqualTo("/api/v1/invoices/inv-1")).willReturn(
                aResponse().withStatus(503),
            ),
        )
        // auth-service 도 정상 — 주문자 조인은 성공.
        auth.stubFor(
            get(urlPathEqualTo("/api/v1/users/u-1")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """{"id":"u-1","email":"u1@example.com","displayName":"홍길동","roles":["USER"],"status":"ACTIVE","createdAt":"2026-05-01T00:00:00Z"}""",
                    ),
            ),
        )

        val response = graphQlTester.document(
            """
            query {
              order(id: "o-1") {
                id
                status
                user { id email }
                invoice { id }
              }
            }
            """.trimIndent(),
        ).execute()

        // billing-platform 장애로 invoice 조인만 오류 — error 배열에 downstream 식별자가 실린다.
        // path 검증 전에 errors 를 먼저 소비해야 GraphQlTester 가 부분 실패를 허용한다.
        response.errors()
            .satisfy { errors ->
                require(errors.size == 1) { "오류는 invoice 조인 1건만 있어야 한다" }
                val err = errors.single()
                require(err.path == "order.invoice")
                require(err.extensions["classification"] == "DOWNSTREAM_UNAVAILABLE")
                require(err.extensions["service"] == "billing")
            }

        // 주문 본문과 user 조인은 살아 있다 (부분 실패 — 쿼리 전체가 죽지 않는다).
        response.path("order.id").entity(String::class.java).isEqualTo("o-1")
        response.path("order.user.email").entity(String::class.java).isEqualTo("u1@example.com")
        // billing-platform 장애로 invoice 만 null.
        response.path("order.invoice").valueIsNull()
    }

    @Test
    fun `downstream 이 404 면 단건 조회는 null 을 반환한다 (오류 아님)`() {
        auth.stubFor(
            get(urlPathEqualTo("/api/v1/users/missing")).willReturn(aResponse().withStatus(404)),
        )

        graphQlTester.document("""query { user(id: "missing") { id } }""")
            .execute()
            .errors().verify()  // 404 는 오류로 새지 않는다 — errors 배열이 비어야 한다.
            .path("user").valueIsNull()
    }
}
