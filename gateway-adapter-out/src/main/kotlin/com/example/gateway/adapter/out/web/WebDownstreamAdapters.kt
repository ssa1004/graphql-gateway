package com.example.gateway.adapter.out.web

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

/**
 * 9개 downstream service 의 REST 를 호출하는 WebClient 어댑터 묶음.
 *
 * 헥사고날 outbound adapter — application 의 downstream port 를 구현한다. 모든 호출은
 * [ResilientDownstreamClient] 를 거쳐 서킷 브레이커 / 재시도 / 타임아웃이 입혀진다.
 *
 * `gateway.downstream.stub=false` (기본값) 일 때만 빈으로 등록된다. stub 모드면 이 어댑터
 * 대신 in-memory stub 어댑터(StubAdapters) 가 같은 port 를 채운다 — downstream 9 service 가
 * 안 떠 있어도 게이트웨이를 띄울 수 있게 하기 위한 구성이다.
 *
 * batch 메서드(findByIds 등)는 downstream 이 bulk 조회를 지원하지 않는다고 보고 단건 호출을
 * coroutine 으로 병렬 fan-out 한다. DataLoader 가 N개의 키를 모아 한 번 호출하므로,
 * GraphQL 쿼리 레벨의 N+1 은 사라지고 게이트웨이->downstream 호출만 병렬 N개가 된다.
 */

private const val BULK_CONCURRENCY_NOTE = "DataLoader 가 키를 모아 호출 — fan-out 은 게이트웨이 내부 병렬"

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebUserAdapter(
    @Qualifier("authWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : UserPort {

    override suspend fun findById(id: String): User? =
        client.get("auth", webClient, "/api/v1/users/$id", UserDto::class.java)?.toDomain()

    override suspend fun findByIds(ids: Set<String>): Map<String, User> = coroutineScope {
        // BULK_CONCURRENCY_NOTE 참고 — 단건 호출 병렬 fan-out.
        ids.map { id -> async { id to findById(id) } }
            .awaitAll()
            .mapNotNull { (id, user) -> user?.let { id to it } }
            .toMap()
    }
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebOrderAdapter(
    @Qualifier("commerceWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : OrderPort {

    override suspend fun findById(id: String): Order? =
        client.get("commerce", webClient, "/api/v1/orders/$id", OrderDto::class.java)?.toDomain()
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebTradeAdapter(
    @Qualifier("marketplaceWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : TradePort {

    override suspend fun findById(id: String): Trade? =
        client.get("marketplace", webClient, "/api/v1/trades/$id", TradeDto::class.java)?.toDomain()
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebInvoiceAdapter(
    @Qualifier("billingWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : InvoicePort {

    override suspend fun findById(id: String): Invoice? =
        client.get("billing", webClient, "/api/v1/invoices/$id", InvoiceDto::class.java)?.toDomain()

    override suspend fun findByIds(ids: Set<String>): Map<String, Invoice> = coroutineScope {
        ids.map { id -> async { id to findById(id) } }
            .awaitAll()
            .mapNotNull { (id, invoice) -> invoice?.let { id to it } }
            .toMap()
    }
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebGpuJobAdapter(
    @Qualifier("gpuWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : GpuJobPort {

    override suspend fun findById(id: String): GpuJob? =
        client.get("gpu", webClient, "/api/v1/jobs/$id", GpuJobDto::class.java)?.toDomain()
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebNotificationAdapter(
    @Qualifier("notificationWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : NotificationPort {

    override suspend fun findByUserIds(userIds: Set<String>, limitPerUser: Int): Map<String, List<Notification>> =
        coroutineScope {
            userIds.map { userId ->
                async {
                    val uri = UriComponentsBuilder.fromPath("/api/v1/notifications")
                        .queryParam("userId", userId)
                        .queryParam("limit", limitPerUser)
                        .build().toUriString()
                    val page = client.get("notification", webClient, uri, NotificationPageDto::class.java)
                    userId to (page?.items?.map { it.toDomain() } ?: emptyList())
                }
            }.awaitAll().toMap()
        }

    override suspend fun markRead(id: String): Notification? =
        client.post("notification", webClient, "/api/v1/notifications/$id/read", NotificationDto::class.java)
            ?.toDomain()
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebFeedAdapter(
    @Qualifier("feedWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : FeedPort {

    override suspend fun findBySkuIds(skuIds: Set<String>): Map<String, Feed> = coroutineScope {
        skuIds.map { skuId ->
            async {
                val feed = client.get("feed", webClient, "/api/v1/feeds/by-sku/$skuId", FeedDto::class.java)
                skuId to feed?.toDomain()
            }
        }.awaitAll()
            .mapNotNull { (skuId, feed) -> feed?.let { skuId to it } }
            .toMap()
    }
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebSearchAdapter(
    @Qualifier("searchWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : SearchPort {

    override suspend fun search(query: String, offset: Int, limit: Int): Connection<SearchHit> {
        val uri = UriComponentsBuilder.fromPath("/api/v1/search")
            .queryParam("q", query)
            .queryParam("offset", offset)
            .queryParam("limit", limit)
            .build().toUriString()
        val page = client.get("search", webClient, uri, SearchHitPageDto::class.java)
        val hits = page?.items?.map { it.toDomain() } ?: emptyList()
        return toConnection(hits, offset, limit, page?.total ?: hits.size)
    }
}

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebSecurityAlertAdapter(
    @Qualifier("securityLogWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
) : SecurityAlertPort {

    override suspend fun findByTenant(tenantId: String, offset: Int, limit: Int): Connection<SecurityAlert> {
        val uri = UriComponentsBuilder.fromPath("/api/v1/alerts")
            .queryParam("tenantId", tenantId)
            .queryParam("offset", offset)
            .queryParam("limit", limit)
            .build().toUriString()
        val page = client.get("security-log", webClient, uri, SecurityAlertPageDto::class.java)
        val alerts = page?.items?.map { it.toDomain() } ?: emptyList()
        return toConnection(alerts, offset, limit, page?.total ?: alerts.size)
    }
}

/**
 * offset/limit 결과를 Relay cursor connection 으로 변환한다 (ADR-0006).
 * 제네릭 [PageDto] 는 Jackson 의 타입 소거 때문에 역직렬화가 불안정하므로, service 별
 * 구체 page DTO 를 둔다 (아래).
 */
private fun <T> toConnection(items: List<T>, offset: Int, limit: Int, total: Int): Connection<T> {
    val edges = items.mapIndexed { index, node ->
        Edge(cursor = CursorCodec.encode(offset + index), node = node)
    }
    val hasNext = offset + items.size < total
    return Connection(
        edges = edges,
        pageInfo = PageInfo(hasNextPage = hasNext, endCursor = edges.lastOrNull()?.cursor),
        totalCount = total,
    )
}
