package com.example.gateway.adapter.out.web

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * downstream 9 service 의 REST 응답 DTO.
 *
 * 각 service 가 내려주는 JSON 모양을 그대로 받는 전송 객체다. 도메인 모델과 분리해 두는
 * 이유: downstream 스펙이 바뀌어도 여기와 매퍼만 고치면 GraphQL schema 와 resolver 는
 * 그대로 간다 (ADR-0009). `@JsonIgnoreProperties(ignoreUnknown = true)` 로 downstream 이
 * 필드를 추가해도 역직렬화가 깨지지 않게 한다 (느슨한 호환).
 */

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserDto(
    val id: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val roles: List<String>? = null,
    val status: String? = null,
    val createdAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderDto(
    val id: String? = null,
    val userId: String? = null,
    val status: String? = null,
    val totalAmount: Int? = null,
    val currency: String? = null,
    val placedAt: String? = null,
    val invoiceId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TradeDto(
    val id: String? = null,
    val skuId: String? = null,
    val buyerId: String? = null,
    val sellerId: String? = null,
    val price: Int? = null,
    val status: String? = null,
    val matchedAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InvoiceDto(
    val id: String? = null,
    val tenantId: String? = null,
    val period: String? = null,
    val totalAmount: Int? = null,
    val currency: String? = null,
    val status: String? = null,
    val issuedAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GpuJobDto(
    val id: String? = null,
    val name: String? = null,
    val status: String? = null,
    val gpuType: String? = null,
    val submittedBy: String? = null,
    val submittedAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotificationDto(
    val id: String? = null,
    val userId: String? = null,
    val channel: String? = null,
    val title: String? = null,
    val body: String? = null,
    val read: Boolean? = null,
    val sentAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FeedDto(
    val id: String? = null,
    val skuId: String? = null,
    val topic: String? = null,
    val lastEventAt: String? = null,
    val subscriberCount: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SecurityAlertDto(
    val id: String? = null,
    val tenantId: String? = null,
    val severity: String? = null,
    val ruleName: String? = null,
    val message: String? = null,
    val detectedAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchHitDto(
    val id: String? = null,
    val type: String? = null,
    val title: String? = null,
    val score: Double? = null,
)

/**
 * offset/limit 페이징 목록 응답.
 *
 * 제네릭 `PageDto<T>` 대신 service 별 구체 DTO 를 둔다 — Jackson 은 런타임 타입 소거 때문에
 * `List<T>` 의 원소 타입을 잃어버려 LinkedHashMap 으로 잘못 역직렬화한다. 구체 타입이면 안전하다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchHitPageDto(
    val items: List<SearchHitDto>? = null,
    val total: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SecurityAlertPageDto(
    val items: List<SecurityAlertDto>? = null,
    val total: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotificationPageDto(
    val items: List<NotificationDto>? = null,
    val total: Int? = null,
)
