package com.example.gateway.adapter.out.stub

import com.example.gateway.application.port.FeedPort
import com.example.gateway.application.port.GpuJobPort
import com.example.gateway.application.port.InvoicePort
import com.example.gateway.application.port.NotificationPort
import com.example.gateway.application.port.OrderPort
import com.example.gateway.application.port.SearchPort
import com.example.gateway.application.port.SecurityAlertPort
import com.example.gateway.application.port.TradePort
import com.example.gateway.application.port.UserPort
import com.example.gateway.domain.Connection
import com.example.gateway.domain.CursorCodec
import com.example.gateway.domain.Edge
import com.example.gateway.domain.Feed
import com.example.gateway.domain.GpuJob
import com.example.gateway.domain.Invoice
import com.example.gateway.domain.Notification
import com.example.gateway.domain.Order
import com.example.gateway.domain.PageInfo
import com.example.gateway.domain.SearchHit
import com.example.gateway.domain.SecurityAlert
import com.example.gateway.domain.Trade
import com.example.gateway.domain.User
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * stub 모드용 in-memory downstream 어댑터 묶음.
 *
 * `gateway.downstream.stub=true` 일 때만 빈으로 등록된다. WebClient 어댑터와 같은 port 를
 * 구현하므로 application/adapter-in 은 어느 쪽이 붙었는지 모른다 (헥사고날 — port 가 경계).
 *
 * downstream 9 service 가 안 떠 있어도 게이트웨이를 띄우고 GraphQL playground / 데모
 * 스크립트 / 회귀 테스트를 돌릴 수 있게 하는 것이 목적이다. [StubData] 의 결정적 데이터셋을
 * 그대로 반환한다.
 */

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubUserAdapter : UserPort {
    override suspend fun findById(id: String): User? = StubData.users[id]
    override suspend fun findByIds(ids: Set<String>): Map<String, User> =
        ids.mapNotNull { id -> StubData.users[id]?.let { id to it } }.toMap()
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubOrderAdapter : OrderPort {
    override suspend fun findById(id: String): Order? = StubData.orders[id]
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubTradeAdapter : TradePort {
    override suspend fun findById(id: String): Trade? = StubData.trades[id]
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubInvoiceAdapter : InvoicePort {
    override suspend fun findById(id: String): Invoice? = StubData.invoices[id]
    override suspend fun findByIds(ids: Set<String>): Map<String, Invoice> =
        ids.mapNotNull { id -> StubData.invoices[id]?.let { id to it } }.toMap()
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubGpuJobAdapter : GpuJobPort {
    override suspend fun findById(id: String): GpuJob? = StubData.jobs[id]
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubNotificationAdapter : NotificationPort {
    override suspend fun findByUserIds(userIds: Set<String>, limitPerUser: Int): Map<String, List<Notification>> =
        userIds.associateWith { userId ->
            (StubData.notificationsByUser[userId] ?: emptyList()).take(limitPerUser)
        }

    override suspend fun markRead(id: String): Notification? =
        StubData.notificationsByUser.values.flatten().firstOrNull { it.id == id }?.copy(read = true)
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubFeedAdapter : FeedPort {
    override suspend fun findBySkuIds(skuIds: Set<String>): Map<String, Feed> =
        skuIds.mapNotNull { skuId -> StubData.feedsBySku[skuId]?.let { skuId to it } }.toMap()
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubSearchAdapter : SearchPort {
    override suspend fun search(query: String, offset: Int, limit: Int): Connection<SearchHit> =
        StubPaging.page(StubData.searchHits(query), offset, limit)
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "true")
class StubSecurityAlertAdapter : SecurityAlertPort {
    override suspend fun findByTenant(tenantId: String, offset: Int, limit: Int): Connection<SecurityAlert> =
        StubPaging.page(StubData.securityAlerts(tenantId), offset, limit)
}

/** stub 목록을 offset/limit 으로 잘라 cursor connection 으로 만든다. */
private object StubPaging {
    fun <T> page(all: List<T>, offset: Int, limit: Int): Connection<T> {
        val window = all.drop(offset).take(limit)
        val edges = window.mapIndexed { index, node ->
            Edge(cursor = CursorCodec.encode(offset + index), node = node)
        }
        return Connection(
            edges = edges,
            pageInfo = PageInfo(
                hasNextPage = offset + window.size < all.size,
                endCursor = edges.lastOrNull()?.cursor,
            ),
            totalCount = all.size,
        )
    }
}
