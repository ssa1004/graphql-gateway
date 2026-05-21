package com.example.gateway.adapter.graphql

import com.example.gateway.application.usecase.GatewayMutationService
import com.example.gateway.domain.Notification
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

/**
 * GraphQL Mutation 루트 resolver.
 *
 * 이 gateway 는 조회(BFF) 위주이고 쓰기는 각 service REST 를 직접 호출하는 것이 원칙이라
 * mutation 표면을 최소로 둔다 (ADR-0001). 알림 읽음 처리만 노출한다.
 */
@Controller
class MutationController(
    private val mutationService: GatewayMutationService,
) {
    @MutationMapping
    suspend fun markNotificationRead(@Argument id: String): Notification? =
        mutationService.markNotificationRead(id)
}
