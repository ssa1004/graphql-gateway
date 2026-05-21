package com.example.gateway.adapter.graphql

import com.example.gateway.application.usecase.GatewayQueryService
import com.example.gateway.domain.Connection
import com.example.gateway.domain.CursorCodec
import com.example.gateway.domain.GpuJob
import com.example.gateway.domain.Invoice
import com.example.gateway.domain.Order
import com.example.gateway.domain.SearchHit
import com.example.gateway.domain.SecurityAlert
import com.example.gateway.domain.Trade
import com.example.gateway.domain.User
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/**
 * GraphQL Query 루트 resolver — 9개 service 의 진입점.
 *
 * `@QueryMapping` 메서드 이름이 schema 의 `Query` 필드와 1:1로 매핑된다. 모든 resolver 는
 * suspend — downstream 호출이 non-blocking 이고, Spring for GraphQL 이 suspend 함수를
 * 그대로 지원한다.
 *
 * 단건 조회는 대상이 없으면 null 을 돌려준다 (schema 가 nullable). 목록은 cursor
 * connection 으로 페이징한다 (ADR-0006).
 *
 * 조인 필드(Order.invoice 등)는 여기 없다 — [FederationController] 가 `@SchemaMapping` +
 * DataLoader 로 따로 처리한다 (N+1 방지, ADR-0002).
 */
@Controller
class QueryController(
    private val queryService: GatewayQueryService,
) {
    @QueryMapping
    suspend fun user(@Argument id: String): User? = queryService.user(id)

    @QueryMapping
    suspend fun order(@Argument id: String): Order? = queryService.order(id)

    @QueryMapping
    suspend fun trade(@Argument id: String): Trade? = queryService.trade(id)

    @QueryMapping
    suspend fun invoice(@Argument id: String): Invoice? = queryService.invoice(id)

    @QueryMapping
    suspend fun job(@Argument id: String): GpuJob? = queryService.job(id)

    @QueryMapping
    suspend fun search(
        @Argument q: String,
        @Argument first: Int,
        @Argument after: String?,
    ): Connection<SearchHit> =
        queryService.search(q, startOffset(after), first.coerceLimit())

    @QueryMapping
    suspend fun securityAlerts(
        @Argument tenantId: String,
        @Argument first: Int,
        @Argument after: String?,
    ): Connection<SecurityAlert> =
        queryService.securityAlerts(tenantId, startOffset(after), first.coerceLimit())
}

/**
 * Relay `after` cursor 를 다음 페이지의 시작 offset 으로 변환한다.
 *
 * `after` 는 "이 cursor *다음* 부터" 를 뜻한다 (Relay 규약). cursor 는 본 게이트웨이에서
 * 마지막으로 본 항목의 offset 을 감싼 값이므로, 다음 페이지는 `그 offset + 1` 에서 시작한다.
 * `after` 가 없으면 처음(0)부터.
 */
private fun startOffset(after: String?): Int =
    after?.let { CursorCodec.decode(it) + 1 } ?: 0

/**
 * `first` 인자를 안전 범위로 자른다. 한 페이지에 100건을 넘게 요청하면 100으로 깎는다 —
 * 거대한 페이지로 downstream 을 끌어가는 것을 막는 1차 방어 (DoS, ADR-0008 의 보조 장치).
 */
private fun Int.coerceLimit(): Int = this.coerceIn(1, 100)
