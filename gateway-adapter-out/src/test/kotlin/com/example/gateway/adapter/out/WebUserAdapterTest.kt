package com.example.gateway.adapter.out

import com.example.gateway.adapter.out.web.ResilientDownstreamClient
import com.example.gateway.adapter.out.web.WebUserAdapter
import com.example.gateway.application.port.DownstreamException
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/**
 * [WebUserAdapter] + [ResilientDownstreamClient] 단위 테스트.
 *
 * Spring 컨텍스트 없이 WireMock 으로 auth-service 를 흉내내, WebClient 호출 / DTO 매핑 /
 * Resilience4j (404 / 5xx) 동작을 직접 검증한다.
 */
class WebUserAdapterTest {

    private lateinit var auth: WireMockServer
    private lateinit var adapter: WebUserAdapter

    @BeforeEach
    fun setUp() {
        auth = WireMockServer(options().dynamicPort())
        auth.start()

        // 404 는 회로/재시도 실패로 집계하지 않도록 ignore 등록 (application.yml 과 같은 정책).
        val notFound = listOf(com.example.gateway.adapter.out.web.NotFoundSignal::class.java)
        val client = ResilientDownstreamClient(
            circuitBreakerRegistry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                    .ignoreExceptions(*notFound.toTypedArray())
                    .build(),
            ),
            retryRegistry = RetryRegistry.of(
                RetryConfig.custom<Any>()
                    .maxAttempts(2)
                    .ignoreExceptions(*notFound.toTypedArray())
                    .build(),
            ),
            timeLimiterRegistry = TimeLimiterRegistry.of(
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build(),
            ),
        )
        adapter = WebUserAdapter(WebClient.builder().baseUrl(auth.baseUrl()).build(), client)
    }

    @AfterEach
    fun tearDown() {
        auth.stop()
    }

    @Test
    fun `200 응답을 도메인 User 로 매핑한다`(): Unit = runBlocking {
        auth.stubFor(
            get(urlPathEqualTo("/api/v1/users/u-1")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """{"id":"u-1","email":"u1@example.com","displayName":"길동","roles":["ADMIN"],"status":"ACTIVE","createdAt":"2026-05-01T00:00:00Z"}""",
                    ),
            ),
        )

        val user = adapter.findById("u-1")

        assertThat(user).isNotNull
        assertThat(user!!.email).isEqualTo("u1@example.com")
        assertThat(user.roles).containsExactly("ADMIN")
    }

    @Test
    fun `404 응답은 null 을 반환한다 (예외 아님)`(): Unit = runBlocking {
        auth.stubFor(
            get(urlPathEqualTo("/api/v1/users/missing")).willReturn(aResponse().withStatus(404)),
        )

        assertThat(adapter.findById("missing")).isNull()
    }

    @Test
    fun `5xx 응답은 DownstreamException 으로 변환된다`(): Unit = runBlocking {
        auth.stubFor(
            get(urlPathEqualTo("/api/v1/users/u-err")).willReturn(aResponse().withStatus(503)),
        )

        val thrown = runCatching { adapter.findById("u-err") }.exceptionOrNull()

        assertThat(thrown)
            .isInstanceOf(DownstreamException::class.java)
        assertThat((thrown as DownstreamException).serviceName).isEqualTo("auth")
    }

    @Test
    fun `findByIds 는 존재하는 id 만 모아 맵으로 반환한다`(): Unit = runBlocking {
        auth.stubFor(
            get(urlPathEqualTo("/api/v1/users/u-1")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"id":"u-1","email":"u1@example.com","displayName":"a","roles":[],"status":"ACTIVE","createdAt":""}"""),
            ),
        )
        auth.stubFor(
            get(urlPathEqualTo("/api/v1/users/u-2")).willReturn(aResponse().withStatus(404)),
        )

        val result = adapter.findByIds(setOf("u-1", "u-2"))

        assertThat(result.keys).containsExactly("u-1")
    }
}
