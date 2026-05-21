package com.example.gateway.adapter.out.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * downstream 호출용 WebClient 묶음.
 *
 * service 별로 base URL 만 다른 WebClient 를 따로 만들어 둔다. 연결/읽기 타임아웃은
 * Resilience4j TimeLimiter 와 별개로 transport 레벨에서도 한 번 더 건다 — 소켓이
 * 영원히 매달리는 상황을 막기 위한 안전망이다.
 *
 * 빈 이름이 곧 service 식별자 — 각 어댑터가 @Qualifier 로 자기 WebClient 를 주입받는다.
 */
@Configuration(proxyBeanMethods = false)
class DownstreamWebClientConfig {

    private fun httpClient(timeoutMs: Long): HttpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs.toInt())
            .responseTimeout(Duration.ofMillis(timeoutMs))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
            }

    private fun client(baseUrl: String, timeoutMs: Long): WebClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient(timeoutMs)))
            .build()

    @Bean
    fun authWebClient(props: DownstreamProperties): WebClient =
        client(props.auth.baseUrl, props.timeoutMs)

    @Bean
    fun commerceWebClient(props: DownstreamProperties): WebClient =
        client(props.commerce.baseUrl, props.timeoutMs)

    @Bean
    fun marketplaceWebClient(props: DownstreamProperties): WebClient =
        client(props.marketplace.baseUrl, props.timeoutMs)

    @Bean
    fun billingWebClient(props: DownstreamProperties): WebClient =
        client(props.billing.baseUrl, props.timeoutMs)

    @Bean
    fun gpuWebClient(props: DownstreamProperties): WebClient =
        client(props.gpu.baseUrl, props.timeoutMs)

    @Bean
    fun notificationWebClient(props: DownstreamProperties): WebClient =
        client(props.notification.baseUrl, props.timeoutMs)

    @Bean
    fun feedWebClient(props: DownstreamProperties): WebClient =
        client(props.feed.baseUrl, props.timeoutMs)

    @Bean
    fun searchWebClient(props: DownstreamProperties): WebClient =
        client(props.search.baseUrl, props.timeoutMs)

    @Bean
    fun securityLogWebClient(props: DownstreamProperties): WebClient =
        client(props.securityLog.baseUrl, props.timeoutMs)
}
