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
import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.util.Optional

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

@Component
@ConditionalOnProperty(prefix = "gateway.downstream", name = ["stub"], havingValue = "false", matchIfMissing = true)
class WebUserAdapter(
    @Qualifier("authWebClient") private val webClient: WebClient,
    private val client: ResilientDownstreamClient,
    @Value("\${gateway.cache.user.ttl-seconds:60}") ttlSeconds: Long = 60,
    @Value("\${gateway.cache.user.max-size:10000}") maxSize: Long = 10_000,
) : UserPort {

    // ADR-0007: 사용자 프로필처럼 분 단위로 거의 안 바뀌는 데이터만 단기 캐시한다(거래/job/알림은 캐시 안 함).
    // AsyncCache 는 같은 키의 동시 미스를 한 번의 downstream 로드로 합쳐 stampede(떼몰림)를 막는다.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache: AsyncCache<String, Optional<User>> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
        .maximumSize(maxSize)
        .buildAsync()

    override suspend fun findById(id: String): User? =
        cache.get(id) { key, _ -> scope.future { Optional.ofNullable(fetch(key)) } }.await().orElse(null)

    override suspend fun findByIds(ids: Set<String>): Map<String, User> =
        parallelByKeys(ids) { findById(it) }

    private suspend fun fetch(id: String): User? =
        client.get("auth", webClient, "/api/v1/users/$id", UserDto::class.java)?.toDomain()

    @PreDestroy
    private fun close() = scope.cancel()
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

    override suspend fun findByIds(ids: Set<String>): Map<String, Invoice> =
        parallelByKeys(ids) { findById(it) }
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
        parallelByKeys(userIds) { userId ->
            val uri = UriComponentsBuilder.fromPath("/api/v1/notifications")
                .queryParam("userId", userId)
                .queryParam("limit", limitPerUser)
                .build().toUriString()
            val page = client.get("notification", webClient, uri, NotificationPageDto::class.java)
            // 항상 비-null 리스트를 반환 — 알림이 없는 사용자도 키가 빈 목록으로 유지된다.
            page?.items?.map { it.toDomain() } ?: emptyList()
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

    override suspend fun findBySkuIds(skuIds: Set<String>): Map<String, Feed> =
        parallelByKeys(skuIds) { skuId ->
            client.get("feed", webClient, "/api/v1/feeds/by-sku/$skuId", FeedDto::class.java)?.toDomain()
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
        return toConnection(hits, offset, page?.total ?: hits.size)
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
        return toConnection(alerts, offset, page?.total ?: alerts.size)
    }
}

/**
 * 키 집합을 단건 호출로 병렬 fan-out 해 키->값 맵으로 모은다. 값이 없는(null) 키는 맵에서 빠진다.
 *
 * DataLoader 의 batch 함수가 호출하는 어댑터 메서드(`findByIds` 등) 공통 구현이다.
 * downstream 이 bulk 조회를 지원하지 않으므로 단건 호출을 coroutine 으로 동시에 날린다 —
 * GraphQL 쿼리 레벨의 N+1(직렬 N회)은 사라지고 게이트웨이 내부 병렬 호출 한 묶음이 된다.
 */
private suspend fun <K, V> parallelByKeys(keys: Set<K>, fetch: suspend (K) -> V?): Map<K, V> =
    coroutineScope {
        keys.map { key -> async { key to fetch(key) } }
            .awaitAll()
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .toMap()
    }

/**
 * offset/limit 결과를 Relay cursor connection 으로 변환한다 (ADR-0006).
 * cursor 는 항목의 offset 을 [CursorCodec] 으로 감싼 불투명 문자열이다.
 */
private fun <T> toConnection(items: List<T>, offset: Int, total: Int): Connection<T> {
    val edges = items.mapIndexed { index, node ->
        Edge(cursor = CursorCodec.encode(offset + index), node = node)
    }
    return Connection(
        edges = edges,
        pageInfo = PageInfo(hasNextPage = offset + items.size < total, endCursor = edges.lastOrNull()?.cursor),
        totalCount = total,
    )
}
