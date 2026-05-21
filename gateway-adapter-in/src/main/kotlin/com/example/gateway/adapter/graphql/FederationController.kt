package com.example.gateway.adapter.graphql

import com.example.gateway.domain.Feed
import com.example.gateway.domain.GpuJob
import com.example.gateway.domain.Invoice
import com.example.gateway.domain.Notification
import com.example.gateway.domain.Order
import com.example.gateway.domain.Trade
import com.example.gateway.domain.User
import org.dataloader.DataLoader
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.util.concurrent.CompletableFuture

/**
 * federation 조인 resolver — 한 service 의 타입에서 다른 service 의 데이터를 잇는다.
 *
 * `@SchemaMapping` 메서드는 부모 타입의 필드를 풀어준다. 예: `Order.invoice` 는
 * Order(commerce-ops) 에서 Invoice(billing-platform) 로 가는 조인이다.
 *
 * 핵심은 DataLoader — resolver 가 downstream 을 직접 호출하지 않고 [DataLoader.load] 로
 * 키만 등록한다. graphql-java 가 같은 tick 의 키를 모아 batch 함수를 한 번 호출하므로
 * N+1 이 사라진다 (ADR-0002). DataLoader 는 [DataLoaderConfig] 가 등록하고, 여기서는
 * 메서드 파라미터로 주입받는다 — 파라미터 이름이 DataLoader 이름과 일치해야 한다.
 *
 * 반환 타입이 [CompletableFuture] 인 이유: DataLoader.load 는 즉시 값을 주지 않고 batch
 * 가 끝날 때 완성되는 future 를 준다. Spring for GraphQL 이 이를 비동기로 처리한다.
 */
@Controller
class FederationController {

    // --- Order 조인 -----------------------------------------------------------

    /** Order.user — 주문자(auth-service). userId 를 userLoader 에 등록. */
    @SchemaMapping(typeName = "Order", field = "user")
    fun orderUser(
        order: Order,
        userLoader: DataLoader<String, User>,
    ): CompletableFuture<User?> = userLoader.load(order.userId)

    /**
     * Order.invoice — 주문의 인보이스(billing-platform).
     * invoiceId 가 없으면 DataLoader 를 거치지 않고 바로 null 을 완료시킨다.
     */
    @SchemaMapping(typeName = "Order", field = "invoice")
    fun orderInvoice(
        order: Order,
        invoiceLoader: DataLoader<String, Invoice>,
    ): CompletableFuture<Invoice?> =
        order.invoiceId?.let { invoiceLoader.load(it) }
            ?: CompletableFuture.completedFuture(null)

    // --- User 조인 ------------------------------------------------------------

    /** User.notifications — 사용자의 최근 알림(notification-hub). */
    @SchemaMapping(typeName = "User", field = "notifications")
    fun userNotifications(
        user: User,
        notificationsLoader: DataLoader<String, List<Notification>>,
    ): CompletableFuture<List<Notification>> = notificationsLoader.load(user.id)

    // --- Trade 조인 -----------------------------------------------------------

    /** Trade.feed — 체결 상품의 실시간 피드(realtime-feed-service). skuId 로 조인. */
    @SchemaMapping(typeName = "Trade", field = "feed")
    fun tradeFeed(
        trade: Trade,
        feedLoader: DataLoader<String, Feed>,
    ): CompletableFuture<Feed?> = feedLoader.load(trade.skuId)

    // --- GpuJob 조인 ----------------------------------------------------------

    /** GpuJob.submitter — job 제출자(auth-service). Order.user 와 같은 userLoader 를 공유. */
    @SchemaMapping(typeName = "GpuJob", field = "submitter")
    fun jobSubmitter(
        job: GpuJob,
        userLoader: DataLoader<String, User>,
    ): CompletableFuture<User?> = userLoader.load(job.submittedBy)

    // DataLoader 파라미터 이름(userLoader / invoiceLoader / notificationsLoader / feedLoader)은
    // DataLoaderConfig.Names 의 등록 이름과 일치해야 graphql-java 가 같은 인스턴스를 주입한다.
}
