package com.example.gateway.application.usecase

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
import com.example.gateway.domain.Feed
import com.example.gateway.domain.GpuJob
import com.example.gateway.domain.Invoice
import com.example.gateway.domain.Notification
import com.example.gateway.domain.Order
import com.example.gateway.domain.SearchHit
import com.example.gateway.domain.SecurityAlert
import com.example.gateway.domain.Trade
import com.example.gateway.domain.User
import org.springframework.stereotype.Service

/**
 * 게이트웨이 조회 use case.
 *
 * GraphQL resolver(adapter-in) 가 호출하는 application 진입점이다. 단건/목록 조회와 DataLoader
 * 가 쓰는 batch 조회를 한 곳에 모았다. 현재는 downstream port 로의 얇은 위임이지만, 권한
 * 필터링 / 응답 정규화 같은 게이트웨이 공통 규칙이 생기면 이 계층에 둔다.
 *
 * resolver 가 port 를 직접 부르지 않고 이 use case 를 거치게 해서, adapter-in 이 adapter-out 의
 * 포트 구성을 알 필요가 없게 한다(헥사고날 경계).
 */
@Service
class GatewayQueryService(
    private val userPort: UserPort,
    private val orderPort: OrderPort,
    private val tradePort: TradePort,
    private val invoicePort: InvoicePort,
    private val gpuJobPort: GpuJobPort,
    private val notificationPort: NotificationPort,
    private val feedPort: FeedPort,
    private val searchPort: SearchPort,
    private val securityAlertPort: SecurityAlertPort,
) {
    // --- 단건 조회 (Query.* 진입점) -------------------------------------------

    suspend fun user(id: String): User? = userPort.findById(id)

    suspend fun order(id: String): Order? = orderPort.findById(id)

    suspend fun trade(id: String): Trade? = tradePort.findById(id)

    suspend fun invoice(id: String): Invoice? = invoicePort.findById(id)

    suspend fun job(id: String): GpuJob? = gpuJobPort.findById(id)

    // --- 목록 조회 (cursor connection) ----------------------------------------

    suspend fun search(query: String, offset: Int, limit: Int): Connection<SearchHit> =
        searchPort.search(query, offset, limit)

    suspend fun securityAlerts(tenantId: String, offset: Int, limit: Int): Connection<SecurityAlert> =
        securityAlertPort.findByTenant(tenantId, offset, limit)

    // --- DataLoader batch 조회 (federation 조인) -------------------------------

    suspend fun usersByIds(ids: Set<String>): Map<String, User> =
        if (ids.isEmpty()) emptyMap() else userPort.findByIds(ids)

    suspend fun invoicesByIds(ids: Set<String>): Map<String, Invoice> =
        if (ids.isEmpty()) emptyMap() else invoicePort.findByIds(ids)

    suspend fun notificationsByUserIds(userIds: Set<String>, limitPerUser: Int): Map<String, List<Notification>> =
        if (userIds.isEmpty()) emptyMap() else notificationPort.findByUserIds(userIds, limitPerUser)

    suspend fun feedsBySkuIds(skuIds: Set<String>): Map<String, Feed> =
        if (skuIds.isEmpty()) emptyMap() else feedPort.findBySkuIds(skuIds)
}
