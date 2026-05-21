package com.example.gateway.adapter.security

import com.example.gateway.application.port.TokenRelay
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * 토큰 릴레이 interceptor — 들어온 GraphQL 요청의 Bearer 토큰을 downstream 호출용 컨텍스트로 옮긴다.
 *
 * 흐름 (ADR-0004):
 *   1) 클라이언트가 `Authorization: Bearer <jwt>` 헤더로 `/graphql` 호출.
 *   2) Spring Security 가 JWT 서명을 검증 (resource server).
 *   3) 이 interceptor 가 헤더에서 raw 토큰을 꺼내 Reactor Context 에 [TokenRelay.KEY] 로 넣는다.
 *   4) suspend resolver 가 그 컨텍스트를 물려받고, adapter-out 어댑터가 downstream 호출 직전에
 *      [TokenRelay.current] 로 읽어 Authorization 헤더에 다시 싣는다.
 *
 * Reactor Context 를 쓰는 이유는 [TokenRelay] 주석 참고 — coroutine / fan-out 스레드 경계를
 * ThreadLocal 은 못 넘지만 Reactor Context 는 요청 처리 전체에 전파된다.
 */
@Component
class TokenRelayInterceptor : WebGraphQlInterceptor {

    override fun intercept(request: WebGraphQlRequest, chain: WebGraphQlInterceptor.Chain): Mono<WebGraphQlResponse> {
        val token = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val next = chain.next(request)
        // 토큰이 있을 때만 Context 에 싣는다 — Reactor Context 는 null 값을 허용하지 않는다.
        return if (token != null) next.contextWrite { it.put(TokenRelay.KEY, token) } else next
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
