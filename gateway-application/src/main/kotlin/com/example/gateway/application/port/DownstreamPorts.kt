package com.example.gateway.application.port

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

/**
 * Downstream port — 9개 portfolio service 의 REST 를 게이트웨이가 호출하기 위한 인터페이스.
 *
 * 헥사고날 outbound port. 구현은 adapter-out 의 WebClient 어댑터이고, 테스트는 가짜 구현으로
 * 대체한다. 모든 메서드는 suspend — downstream 호출이 non-blocking 이고, resolver 도 suspend 다.
 *
 * 조회 단건 메서드는 대상이 없으면 null 을 반환한다(예외 아님). downstream 장애로 호출이
 * 실패하면 [DownstreamException] 을 던지고, adapter-in 의 error resolver 가 GraphQL 부분
 * 실패로 매핑한다 (ADR-0003 / ADR-0005).
 *
 * `byIds` 형태의 batch 메서드는 DataLoader 가 호출한다 — N+1 방지 (ADR-0002). downstream 이
 * bulk 조회를 지원하지 않으면 어댑터가 내부적으로 병렬 단건 호출로 채운다.
 */

interface UserPort {
    suspend fun findById(id: String): User?

    /** DataLoader batch — 요청한 id 중 존재하는 것만 id->User 로 반환. */
    suspend fun findByIds(ids: Set<String>): Map<String, User>
}

interface OrderPort {
    suspend fun findById(id: String): Order?
}

interface TradePort {
    suspend fun findById(id: String): Trade?
}

interface InvoicePort {
    suspend fun findById(id: String): Invoice?

    /** DataLoader batch — Order.invoice 조인용. */
    suspend fun findByIds(ids: Set<String>): Map<String, Invoice>
}

interface GpuJobPort {
    suspend fun findById(id: String): GpuJob?
}

interface NotificationPort {
    /** User.notifications 조인용 — userId 별 최근 알림. DataLoader 가 userId 집합으로 호출. */
    suspend fun findByUserIds(userIds: Set<String>, limitPerUser: Int): Map<String, List<Notification>>

    suspend fun markRead(id: String): Notification?
}

interface FeedPort {
    /** Trade.feed 조인용 — skuId 별 실시간 피드. DataLoader 가 skuId 집합으로 호출. */
    suspend fun findBySkuIds(skuIds: Set<String>): Map<String, Feed>
}

interface SearchPort {
    suspend fun search(query: String, offset: Int, limit: Int): Connection<SearchHit>
}

interface SecurityAlertPort {
    suspend fun findByTenant(tenantId: String, offset: Int, limit: Int): Connection<SecurityAlert>
}

/**
 * downstream 호출 실패. 서킷 브레이커 open / 타임아웃 / 5xx / 연결 실패를 모두 포괄한다.
 * [serviceName] 으로 어느 service 가 문제인지 식별해 GraphQL error extensions 에 싣는다.
 */
class DownstreamException(
    val serviceName: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
