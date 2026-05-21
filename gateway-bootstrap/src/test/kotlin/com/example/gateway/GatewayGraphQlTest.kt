package com.example.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.ActiveProfiles

/**
 * GraphQL gateway 회귀 테스트 — schema 와 resolver 를 [HttpGraphQlTester] 로 end-to-end 검증.
 *
 * demo 프로필이므로 downstream 은 stub 어댑터(in-memory) 가 채운다. 실제 HTTP 서버를 띄워
 * /graphql 로 쿼리를 보내므로 GraphQL 직렬화 / DataLoader / error 처리 경로가 모두 탄다.
 *
 * 검증 범위:
 *   - 9 service 진입점 단건 조회
 *   - federation 조인 (Order.invoice / Order.user / User.notifications / Trade.feed / GpuJob.submitter)
 *   - DataLoader batch 가 N개 조인을 한 번에 푸는지 (간접 — 조인 결과 정확성)
 *   - cursor connection 페이징 (search / securityAlerts)
 *   - mutation
 *   - 존재하지 않는 id 는 null
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@ActiveProfiles("demo")
class GatewayGraphQlTest {

    @Autowired
    lateinit var graphQlTester: HttpGraphQlTester

    @Test
    fun `Query_user 는 auth-service 사용자를 반환한다`() {
        graphQlTester.document(
            """
            query { user(id: "u-1") { id email displayName roles status } }
            """.trimIndent(),
        )
            .execute()
            .path("user.id").entity(String::class.java).isEqualTo("u-1")
            .path("user.email").entity(String::class.java).isEqualTo("user1@example.com")
            .path("user.roles").entityList(String::class.java).contains("ADMIN")
    }

    @Test
    fun `Query_order 와 Order_invoice 조인이 billing-platform 인보이스를 잇는다`() {
        graphQlTester.document(
            """
            query {
              order(id: "o-1") {
                id
                status
                totalAmount
                user { id email }
                invoice { id status totalAmount }
              }
            }
            """.trimIndent(),
        )
            .execute()
            .path("order.id").entity(String::class.java).isEqualTo("o-1")
            .path("order.user.id").entity(String::class.java).isEqualTo("u-1")
            .path("order.invoice.id").entity(String::class.java).isEqualTo("inv-1")
            .path("order.invoice.status").entity(String::class.java).isEqualTo("ISSUED")
    }

    @Test
    fun `Query_trade 와 Trade_feed 조인이 realtime-feed 를 잇는다`() {
        graphQlTester.document(
            """
            query {
              trade(id: "t-1") {
                id
                price
                status
                feed { id topic subscriberCount }
              }
            }
            """.trimIndent(),
        )
            .execute()
            .path("trade.id").entity(String::class.java).isEqualTo("t-1")
            .path("trade.feed.topic").entity(String::class.java).isEqualTo("market.sku-1")
    }

    @Test
    fun `User_notifications 조인이 notification-hub 알림 목록을 잇는다`() {
        graphQlTester.document(
            """
            query {
              user(id: "u-2") {
                id
                notifications(first: 10) { id channel read }
              }
            }
            """.trimIndent(),
        )
            .execute()
            .path("user.notifications").entityList(Any::class.java).hasSizeGreaterThan(0)
    }

    @Test
    fun `GpuJob_submitter 조인이 auth-service 제출자를 잇는다`() {
        graphQlTester.document(
            """
            query { job(id: "job-1") { id name status submitter { id email } } }
            """.trimIndent(),
        )
            .execute()
            .path("job.id").entity(String::class.java).isEqualTo("job-1")
            .path("job.submitter.id").entity(String::class.java).isEqualTo("u-1")
    }

    @Test
    fun `Query_search 는 cursor connection 으로 페이징한다`() {
        graphQlTester.document(
            """
            query {
              search(q: "노트북", first: 3) {
                totalCount
                pageInfo { hasNextPage endCursor }
                edges { cursor node { id type title score } }
              }
            }
            """.trimIndent(),
        )
            .execute()
            .path("search.edges").entityList(Any::class.java).hasSize(3)
            .path("search.pageInfo.hasNextPage").entity(Boolean::class.java).isEqualTo(true)
            .path("search.totalCount").entity(Int::class.java).isEqualTo(8)
    }

    @Test
    fun `Query_search 의 after cursor 로 다음 페이지를 가져온다`() {
        val firstPage = graphQlTester.document(
            """
            query { search(q: "x", first: 5) { pageInfo { endCursor } } }
            """.trimIndent(),
        )
            .execute()
            .path("search.pageInfo.endCursor").entity(String::class.java).get()

        graphQlTester.document(
            """
            query {
              search(q: "x", first: 5, after: "$firstPage") {
                edges { node { id } }
                pageInfo { hasNextPage }
              }
            }
            """.trimIndent(),
        )
            .execute()
            // 전체 8건, 5건 다음이면 3건 남는다.
            .path("search.edges").entityList(Any::class.java).hasSize(3)
            .path("search.pageInfo.hasNextPage").entity(Boolean::class.java).isEqualTo(false)
    }

    @Test
    fun `Query_securityAlerts 는 security-log-search 알림을 cursor connection 으로 반환한다`() {
        graphQlTester.document(
            """
            query {
              securityAlerts(tenantId: "tenant-9", first: 20) {
                totalCount
                edges { node { id severity ruleName tenantId } }
              }
            }
            """.trimIndent(),
        )
            .execute()
            .path("securityAlerts.totalCount").entity(Int::class.java).isEqualTo(6)
            .path("securityAlerts.edges[0].node.tenantId").entity(String::class.java).isEqualTo("tenant-9")
    }

    @Test
    fun `Mutation_markNotificationRead 는 알림을 읽음 처리한다`() {
        graphQlTester.document(
            """
            mutation { markNotificationRead(id: "ntf-1-2") { id read } }
            """.trimIndent(),
        )
            .execute()
            .path("markNotificationRead.id").entity(String::class.java).isEqualTo("ntf-1-2")
            .path("markNotificationRead.read").entity(Boolean::class.java).isEqualTo(true)
    }

    @Test
    fun `존재하지 않는 id 는 null 을 반환한다`() {
        graphQlTester.document(
            """
            query { user(id: "u-does-not-exist") { id } }
            """.trimIndent(),
        )
            .execute()
            .path("user").valueIsNull()
    }
}
