package com.example.gateway.application.usecase

import com.example.gateway.application.port.NotificationPort
import com.example.gateway.domain.Notification
import org.springframework.stereotype.Service

/**
 * 게이트웨이 mutation use case.
 *
 * 이 gateway 는 조회(BFF) 위주이고 쓰기는 각 service REST 를 직접 호출하는 것이 원칙이라
 * mutation 표면을 최소로 둔다 (ADR-0001). 알림 읽음 처리 정도만 노출한다.
 */
@Service
class GatewayMutationService(
    private val notificationPort: NotificationPort,
) {
    /** 알림 읽음 처리 — notification-hub. 대상이 없으면 null. */
    suspend fun markNotificationRead(id: String): Notification? = notificationPort.markRead(id)
}
