package com.example.gateway.adapter.out.stub

import com.example.gateway.domain.AlertSeverity
import com.example.gateway.domain.Feed
import com.example.gateway.domain.GpuJob
import com.example.gateway.domain.Invoice
import com.example.gateway.domain.InvoiceStatus
import com.example.gateway.domain.JobStatus
import com.example.gateway.domain.Notification
import com.example.gateway.domain.NotificationChannel
import com.example.gateway.domain.Order
import com.example.gateway.domain.OrderStatus
import com.example.gateway.domain.SearchHit
import com.example.gateway.domain.SecurityAlert
import com.example.gateway.domain.Trade
import com.example.gateway.domain.TradeStatus
import com.example.gateway.domain.User
import com.example.gateway.domain.UserStatus

/**
 * stub 모드용 결정적(deterministic) 가짜 데이터.
 *
 * downstream 9 service 가 안 떠 있어도 게이트웨이를 띄우고 GraphQL 쿼리를 시연/테스트할 수
 * 있게 하는 in-memory 데이터셋이다. id 규칙은 단순하게 — `u-1`, `o-1` 처럼 접두사+숫자.
 * 같은 id 는 항상 같은 결과를 주므로 데모 스크립트와 회귀 테스트가 안정적이다.
 *
 * 운영에서는 `gateway.downstream.stub=false` 라 이 데이터는 빈으로 올라오지 않는다.
 */
object StubData {

    private const val TS = "2026-05-21T09:00:00Z"

    val users: Map<String, User> = (1..5).associate { n ->
        "u-$n" to User(
            id = "u-$n",
            email = "user$n@example.com",
            displayName = "데모 사용자 $n",
            roles = if (n == 1) listOf("ADMIN", "USER") else listOf("USER"),
            status = UserStatus.ACTIVE,
            createdAt = TS,
        )
    }

    val orders: Map<String, Order> = (1..3).associate { n ->
        "o-$n" to Order(
            id = "o-$n",
            userId = "u-$n",
            status = OrderStatus.PAID,
            totalAmount = n * 12_000,
            currency = "KRW",
            placedAt = TS,
            invoiceId = "inv-$n",
        )
    }

    val trades: Map<String, Trade> = (1..3).associate { n ->
        "t-$n" to Trade(
            id = "t-$n",
            skuId = "sku-$n",
            buyerId = "u-$n",
            sellerId = "u-${(n % 5) + 1}",
            price = n * 250_000,
            status = TradeStatus.COMPLETED,
            matchedAt = TS,
        )
    }

    val invoices: Map<String, Invoice> = (1..3).associate { n ->
        "inv-$n" to Invoice(
            id = "inv-$n",
            tenantId = "tenant-$n",
            period = "2026-05",
            totalAmount = n * 12_000,
            currency = "KRW",
            status = InvoiceStatus.ISSUED,
            issuedAt = TS,
        )
    }

    val jobs: Map<String, GpuJob> = (1..3).associate { n ->
        "job-$n" to GpuJob(
            id = "job-$n",
            name = "train-model-$n",
            status = JobStatus.SUCCEEDED,
            gpuType = "A100",
            submittedBy = "u-$n",
            submittedAt = TS,
            startedAt = TS,
            finishedAt = TS,
        )
    }

    /** userId -> 알림 목록. User.notifications 조인 시연용. */
    val notificationsByUser: Map<String, List<Notification>> = (1..5).associate { n ->
        "u-$n" to (1..3).map { m ->
            Notification(
                id = "ntf-$n-$m",
                userId = "u-$n",
                channel = NotificationChannel.entries[m % NotificationChannel.entries.size],
                title = "알림 $m",
                body = "데모 사용자 $n 에게 보낸 알림 $m",
                read = m == 1,
                sentAt = TS,
            )
        }
    }

    /** skuId -> 실시간 피드. Trade.feed 조인 시연용. */
    val feedsBySku: Map<String, Feed> = (1..3).associate { n ->
        "sku-$n" to Feed(
            id = "feed-$n",
            topic = "market.sku-$n",
            lastEventAt = TS,
            subscriberCount = n * 7,
        )
    }

    fun searchHits(query: String): List<SearchHit> = (1..8).map { n ->
        SearchHit(
            id = "hit-$n",
            type = if (n % 2 == 0) "product" else "trade",
            title = "'$query' 검색 결과 $n",
            score = (100 - n * 7) / 10.0,
        )
    }

    fun securityAlerts(tenantId: String): List<SecurityAlert> = (1..6).map { n ->
        SecurityAlert(
            id = "alert-$n",
            tenantId = tenantId,
            severity = AlertSeverity.entries[n % AlertSeverity.entries.size],
            ruleName = "rule-$n",
            message = "$tenantId 에서 탐지된 의심 활동 $n",
            detectedAt = TS,
        )
    }
}
