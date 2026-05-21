package com.example.gateway.adapter.dataloader

import com.example.gateway.application.usecase.GatewayQueryService
import com.example.gateway.domain.Feed
import com.example.gateway.domain.Invoice
import com.example.gateway.domain.Notification
import com.example.gateway.domain.User
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.reactor.mono
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Component

/**
 * DataLoader 등록 — GraphQL 의 N+1 쿼리를 batch 호출로 합친다 (ADR-0002).
 *
 * 문제: `order { invoice { ... } }` 를 여러 주문에 대해 펼치면 `Order.invoice` resolver 가
 * 주문마다 따로 호출돼 billing-platform 으로 REST 가 N번 나간다 (N+1).
 *
 * 해결: resolver 는 직접 호출하지 않고 DataLoader 에 키만 등록한다. graphql-java 가 한 tick
 * 동안 쌓인 키를 모아 batch 함수를 한 번 호출하고, 그 결과를 키별로 갈라 각 resolver 에
 * 돌려준다. downstream 호출이 1번(또는 키 수만큼의 병렬 fan-out 1묶음)으로 줄어든다.
 *
 * 4개 조인 필드에 DataLoader 를 둔다:
 *   - userLoader          : Order.user / GpuJob.submitter — auth-service
 *   - invoiceLoader       : Order.invoice — billing-platform
 *   - notificationsLoader : User.notifications — notification-hub
 *   - feedLoader          : Trade.feed — realtime-feed-service
 *
 * batch 함수는 [GatewayQueryService] 의 `*ByIds` 를 호출하고, 그 안에서 adapter-out 이
 * downstream 으로의 병렬 fan-out 을 담당한다. coroutine -> Reactor 변환은 [mono] 로 한다.
 */
@Component
class DataLoaderConfig(
    private val registry: BatchLoaderRegistry,
    private val queryService: GatewayQueryService,
) {
    /** DataLoader 이름 — resolver 가 `@SchemaMapping` 인자로 이 키를 참조한다. */
    object Names {
        const val USER = "userLoader"
        const val INVOICE = "invoiceLoader"
        const val NOTIFICATIONS = "notificationsLoader"
        const val FEED = "feedLoader"
    }

    /** User.notifications 의 사용자별 알림 상한 — schema 의 `notifications(first:)` 기본값과 맞춘다. */
    private val notificationsPerUser = 10

    @PostConstruct
    fun register() {
        // userId 집합 -> User 맵. 키가 없으면 맵에서 빠지고 resolver 는 null 을 받는다.
        registry.forName<String, User>(Names.USER)
            .registerMappedBatchLoader { keys, _ ->
                mono { queryService.usersByIds(keys) }
            }

        // invoiceId 집합 -> Invoice 맵.
        registry.forName<String, Invoice>(Names.INVOICE)
            .registerMappedBatchLoader { keys, _ ->
                mono { queryService.invoicesByIds(keys) }
            }

        // userId 집합 -> 알림 목록 맵. 키가 없으면 빈 목록을 채워 resolver 가 null 안전.
        registry.forName<String, List<Notification>>(Names.NOTIFICATIONS)
            .registerMappedBatchLoader { keys, _ ->
                mono {
                    val byUser = queryService.notificationsByUserIds(keys, notificationsPerUser)
                    keys.associateWith { byUser[it] ?: emptyList() }
                }
            }

        // skuId 집합 -> Feed 맵.
        registry.forName<String, Feed>(Names.FEED)
            .registerMappedBatchLoader { keys, _ ->
                mono { queryService.feedsBySkuIds(keys) }
            }
    }
}
