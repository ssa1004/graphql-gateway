package com.example.gateway.domain

/**
 * GraphQL gateway 의 도메인 모델.
 *
 * 9개 downstream service 의 REST 응답을 게이트웨이 내부 표현으로 정규화한 불변 타입 모음이다.
 * Spring / GraphQL 라이브러리에 의존하지 않는다 — schema 매핑은 adapter-in 의 몫이고,
 * 도메인은 그저 데이터 클래스다 (헥사고날 핵심).
 *
 * downstream DTO 와 이 모델을 분리해 두는 이유: downstream REST 스펙이 바뀌어도
 * 변환 계층(adapter-out) 만 고치면 되고, GraphQL schema 와 resolver 는 그대로다 (ADR-0009).
 */

enum class UserStatus { ACTIVE, SUSPENDED, DELETED }

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val roles: List<String>,
    val status: UserStatus,
    val createdAt: String,
)

enum class OrderStatus { PLACED, PAID, SHIPPED, DELIVERED, CANCELLED }

data class Order(
    val id: String,
    val userId: String,
    val status: OrderStatus,
    val totalAmount: Int,
    val currency: String,
    val placedAt: String,
    /** Order.invoice 조인의 대상 식별자. resolver 가 DataLoader 로 billing-platform 을 조회한다. */
    val invoiceId: String?,
)

enum class TradeStatus {
    MATCHED, PAYMENT_AUTHORIZED, INSPECTION_PASSED, INSPECTION_FAILED, COMPLETED, REFUNDING
}

data class Trade(
    val id: String,
    val skuId: String,
    val buyerId: String,
    val sellerId: String,
    val price: Int,
    val status: TradeStatus,
    val matchedAt: String,
)

enum class InvoiceStatus { DRAFT, ISSUED, PAID, OVERDUE }

data class Invoice(
    val id: String,
    val tenantId: String,
    val period: String,
    val totalAmount: Int,
    val currency: String,
    val status: InvoiceStatus,
    val issuedAt: String?,
)

enum class JobStatus { QUEUED, SCHEDULED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class GpuJob(
    val id: String,
    val name: String,
    val status: JobStatus,
    val gpuType: String,
    val submittedBy: String,
    val submittedAt: String,
    val startedAt: String?,
    val finishedAt: String?,
)

enum class NotificationChannel { EMAIL, PUSH, SMS }

data class Notification(
    val id: String,
    val userId: String,
    val channel: NotificationChannel,
    val title: String,
    val body: String,
    val read: Boolean,
    val sentAt: String,
)

data class Feed(
    val id: String,
    val topic: String,
    val lastEventAt: String,
    val subscriberCount: Int,
)

enum class AlertSeverity { LOW, MEDIUM, HIGH, CRITICAL }

data class SecurityAlert(
    val id: String,
    val tenantId: String,
    val severity: AlertSeverity,
    val ruleName: String,
    val message: String,
    val detectedAt: String,
)

data class SearchHit(
    val id: String,
    val type: String,
    val title: String,
    val score: Double,
)

/**
 * Relay cursor connection — 목록 응답 페이징 공통 표현 (ADR-0006).
 *
 * downstream 은 보통 offset/limit 기반이지만, GraphQL 클라이언트에는 cursor 로 노출한다.
 * cursor 는 [CursorCodec] 이 offset 을 Base64 로 감싼 불투명 문자열이다.
 */
data class Connection<T>(
    val edges: List<Edge<T>>,
    val pageInfo: PageInfo,
    val totalCount: Int,
)

data class Edge<T>(
    val cursor: String,
    val node: T,
)

data class PageInfo(
    val hasNextPage: Boolean,
    val endCursor: String?,
)
