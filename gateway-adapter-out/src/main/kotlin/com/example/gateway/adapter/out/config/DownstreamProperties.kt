package com.example.gateway.adapter.out.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 9개 downstream service 의 접속 정보.
 *
 * `gateway.downstream.*` 로 application.yml 에서 바인딩한다. 각 service 의 base URL 과,
 * 호출 타임아웃을 담는다.
 *
 * [stub] 가 true 면 adapter-out 은 실제 WebClient 어댑터 대신 in-memory stub 어댑터를
 * 빈으로 등록한다 (StubAdapters). downstream 9 service 가 안 떠 있어도 게이트웨이를
 * 띄우고 GraphQL 쿼리를 시연/테스트할 수 있게 하기 위한 스위치다 — 운영에서는 false.
 */
@ConfigurationProperties(prefix = "gateway.downstream")
data class DownstreamProperties(
    /** true 면 in-memory stub 어댑터 사용 (downstream 미가용 환경 대비). */
    val stub: Boolean = false,
    /** 단건 호출 타임아웃 (ms). Resilience4j TimeLimiter 와 WebClient responseTimeout 양쪽에 쓰인다. */
    val timeoutMs: Long = 2000,
    val auth: ServiceEndpoint = ServiceEndpoint("http://localhost:8081"),
    val commerce: ServiceEndpoint = ServiceEndpoint("http://localhost:8082"),
    val marketplace: ServiceEndpoint = ServiceEndpoint("http://localhost:8083"),
    val billing: ServiceEndpoint = ServiceEndpoint("http://localhost:8084"),
    val gpu: ServiceEndpoint = ServiceEndpoint("http://localhost:8085"),
    val notification: ServiceEndpoint = ServiceEndpoint("http://localhost:8086"),
    val feed: ServiceEndpoint = ServiceEndpoint("http://localhost:8087"),
    val search: ServiceEndpoint = ServiceEndpoint("http://localhost:8088"),
    val securityLog: ServiceEndpoint = ServiceEndpoint("http://localhost:8089"),
) {
    data class ServiceEndpoint(val baseUrl: String)
}
