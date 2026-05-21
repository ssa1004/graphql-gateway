package com.example.gateway.adapter.out.web

import com.example.gateway.application.port.DownstreamException
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
 * downstream DTO -> 게이트웨이 도메인 모델 변환.
 *
 * downstream JSON 의 필수 필드가 비어 있으면 [DownstreamException] 을 던진다 — 스펙 위반
 * 응답을 게이트웨이 안쪽으로 흘려보내지 않기 위한 방어다 (ADR-0009). enum 은 알 수 없는
 * 값이 와도 깨지지 않도록 안전 변환 헬퍼를 거친다.
 */

private fun <T> requireField(service: String, value: T?, field: String): T =
    value ?: throw DownstreamException(service, "downstream '$service' 응답에 '$field' 누락")

private inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, default: E): E =
    raw?.let { value -> enumValues<E>().firstOrNull { it.name.equals(value, ignoreCase = true) } } ?: default

fun UserDto.toDomain(): User = User(
    id = requireField("auth-service", id, "id"),
    email = requireField("auth-service", email, "email"),
    displayName = displayName ?: email ?: id ?: "unknown",
    roles = roles ?: emptyList(),
    status = enumOrDefault(status, UserStatus.ACTIVE),
    createdAt = createdAt ?: "",
)

fun OrderDto.toDomain(): Order = Order(
    id = requireField("commerce-ops", id, "id"),
    userId = requireField("commerce-ops", userId, "userId"),
    status = enumOrDefault(status, OrderStatus.PLACED),
    totalAmount = totalAmount ?: 0,
    currency = currency ?: "KRW",
    placedAt = placedAt ?: "",
    invoiceId = invoiceId,
)

fun TradeDto.toDomain(): Trade = Trade(
    id = requireField("bid-ask-marketplace", id, "id"),
    skuId = requireField("bid-ask-marketplace", skuId, "skuId"),
    buyerId = requireField("bid-ask-marketplace", buyerId, "buyerId"),
    sellerId = requireField("bid-ask-marketplace", sellerId, "sellerId"),
    price = price ?: 0,
    status = enumOrDefault(status, TradeStatus.MATCHED),
    matchedAt = matchedAt ?: "",
)

fun InvoiceDto.toDomain(): Invoice = Invoice(
    id = requireField("billing-platform", id, "id"),
    tenantId = requireField("billing-platform", tenantId, "tenantId"),
    period = period ?: "",
    totalAmount = totalAmount ?: 0,
    currency = currency ?: "KRW",
    status = enumOrDefault(status, InvoiceStatus.DRAFT),
    issuedAt = issuedAt,
)

fun GpuJobDto.toDomain(): GpuJob = GpuJob(
    id = requireField("gpu-job-orchestrator", id, "id"),
    name = name ?: "",
    status = enumOrDefault(status, JobStatus.QUEUED),
    gpuType = gpuType ?: "unknown",
    submittedBy = requireField("gpu-job-orchestrator", submittedBy, "submittedBy"),
    submittedAt = submittedAt ?: "",
    startedAt = startedAt,
    finishedAt = finishedAt,
)

fun NotificationDto.toDomain(): Notification = Notification(
    id = requireField("notification-hub", id, "id"),
    userId = requireField("notification-hub", userId, "userId"),
    channel = enumOrDefault(channel, NotificationChannel.EMAIL),
    title = title ?: "",
    body = body ?: "",
    read = read ?: false,
    sentAt = sentAt ?: "",
)

fun FeedDto.toDomain(): Feed = Feed(
    id = requireField("realtime-feed-service", id, "id"),
    topic = topic ?: "",
    lastEventAt = lastEventAt ?: "",
    subscriberCount = subscriberCount ?: 0,
)

fun SecurityAlertDto.toDomain(): SecurityAlert = SecurityAlert(
    id = requireField("security-log-search", id, "id"),
    tenantId = requireField("security-log-search", tenantId, "tenantId"),
    severity = enumOrDefault(severity, AlertSeverity.LOW),
    ruleName = ruleName ?: "",
    message = message ?: "",
    detectedAt = detectedAt ?: "",
)

fun SearchHitDto.toDomain(): SearchHit = SearchHit(
    id = requireField("search-service", id, "id"),
    type = type ?: "unknown",
    title = title ?: "",
    score = score ?: 0.0,
)
