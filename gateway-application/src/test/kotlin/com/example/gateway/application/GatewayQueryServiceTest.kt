package com.example.gateway.application

import com.example.gateway.application.port.FeedPort
import com.example.gateway.application.port.GpuJobPort
import com.example.gateway.application.port.InvoicePort
import com.example.gateway.application.port.NotificationPort
import com.example.gateway.application.port.OrderPort
import com.example.gateway.application.port.SearchPort
import com.example.gateway.application.port.SecurityAlertPort
import com.example.gateway.application.port.TradePort
import com.example.gateway.application.port.UserPort
import com.example.gateway.application.usecase.GatewayQueryService
import com.example.gateway.domain.Connection
import com.example.gateway.domain.Feed
import com.example.gateway.domain.GpuJob
import com.example.gateway.domain.Invoice
import com.example.gateway.domain.Notification
import com.example.gateway.domain.Order
import com.example.gateway.domain.SearchHit
import com.example.gateway.domain.SecurityAlert
import com.example.gateway.domain.Trade
import com.example.gateway.domain.User
import com.example.gateway.domain.UserStatus
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [GatewayQueryService] 단위 테스트 — 가짜 port 구현으로 use case 동작을 검증한다.
 *
 * downstream / Spring / 네트워크 없이 use case 의 위임과 batch 빈-입력 처리를 확인한다.
 */
class GatewayQueryServiceTest {

    private fun user(id: String) = User(id, "$id@example.com", id, listOf("USER"), UserStatus.ACTIVE, "")

    @Test
    fun `user 는 UserPort 에 위임한다`() = runTest {
        val service = service(userPort = object : NoUserPort() {
            override suspend fun findById(id: String) = user(id)
        })
        assertThat(service.user("u-1")?.id).isEqualTo("u-1")
    }

    @Test
    fun `usersByIds 는 빈 입력이면 port 를 호출하지 않고 빈 맵을 반환한다`() = runTest {
        var called = false
        val service = service(userPort = object : NoUserPort() {
            override suspend fun findByIds(ids: Set<String>): Map<String, User> {
                called = true
                return emptyMap()
            }
        })
        assertThat(service.usersByIds(emptySet())).isEmpty()
        assertThat(called).isFalse()
    }

    @Test
    fun `usersByIds 는 비지 않은 입력이면 port 의 batch 결과를 그대로 돌려준다`() = runTest {
        val service = service(userPort = object : NoUserPort() {
            override suspend fun findByIds(ids: Set<String>) = ids.associateWith { user(it) }
        })
        val result = service.usersByIds(setOf("u-1", "u-2"))
        assertThat(result.keys).containsExactlyInAnyOrder("u-1", "u-2")
    }

    @Test
    fun `notificationsByUserIds 는 빈 입력이면 빈 맵을 반환한다`() = runTest {
        val service = service()
        assertThat(service.notificationsByUserIds(emptySet(), 10)).isEmpty()
    }

    // --- 테스트 지원 — 기본은 빈 동작, 필요한 port 만 오버라이드 -----------------

    private fun service(
        userPort: UserPort = NoUserPort(),
        orderPort: OrderPort = OrderPort { null },
        tradePort: TradePort = TradePort { null },
        invoicePort: InvoicePort = NoInvoicePort(),
        gpuJobPort: GpuJobPort = GpuJobPort { null },
        notificationPort: NotificationPort = NoNotificationPort(),
        feedPort: FeedPort = FeedPort { _ -> emptyMap() },
        searchPort: SearchPort = SearchPort { _, _, _ -> emptyConnection() },
        securityAlertPort: SecurityAlertPort = SecurityAlertPort { _, _, _ -> emptyConnection() },
    ) = GatewayQueryService(
        userPort, orderPort, tradePort, invoicePort, gpuJobPort,
        notificationPort, feedPort, searchPort, securityAlertPort,
    )

    private fun <T> emptyConnection(): Connection<T> =
        Connection(emptyList(), com.example.gateway.domain.PageInfo(false, null), 0)

    private open class NoUserPort : UserPort {
        override suspend fun findById(id: String): User? = null
        override suspend fun findByIds(ids: Set<String>): Map<String, User> = emptyMap()
    }

    private open class NoInvoicePort : InvoicePort {
        override suspend fun findById(id: String): Invoice? = null
        override suspend fun findByIds(ids: Set<String>): Map<String, Invoice> = emptyMap()
    }

    private open class NoNotificationPort : NotificationPort {
        override suspend fun findByUserIds(userIds: Set<String>, limitPerUser: Int): Map<String, List<Notification>> =
            emptyMap()

        override suspend fun markRead(id: String): Notification? = null
    }

    // SAM 변환을 쓰기 위한 단일 메서드 port 용 fun interface 어댑터는 불필요 —
    // OrderPort 등은 단일 추상 메서드라 SAM 람다가 바로 된다.
}

// 단일 메서드 port 는 SAM 변환으로 람다를 쓴다 (테스트 가독성).
private fun OrderPort(block: suspend (String) -> Order?) = object : OrderPort {
    override suspend fun findById(id: String) = block(id)
}

private fun TradePort(block: suspend (String) -> Trade?) = object : TradePort {
    override suspend fun findById(id: String) = block(id)
}

private fun GpuJobPort(block: suspend (String) -> GpuJob?) = object : GpuJobPort {
    override suspend fun findById(id: String) = block(id)
}

private fun FeedPort(block: suspend (Set<String>) -> Map<String, Feed>) = object : FeedPort {
    override suspend fun findBySkuIds(skuIds: Set<String>) = block(skuIds)
}

private fun SearchPort(block: suspend (String, Int, Int) -> Connection<SearchHit>) = object : SearchPort {
    override suspend fun search(query: String, offset: Int, limit: Int) = block(query, offset, limit)
}

private fun SecurityAlertPort(block: suspend (String, Int, Int) -> Connection<SecurityAlert>) =
    object : SecurityAlertPort {
        override suspend fun findByTenant(tenantId: String, offset: Int, limit: Int) =
            block(tenantId, offset, limit)
    }
