package com.example.gateway.application.port

import kotlinx.coroutines.reactor.ReactorContext
import kotlin.coroutines.coroutineContext

/**
 * 토큰 릴레이 — 들어온 요청의 Bearer 토큰을 downstream 호출에 그대로 전달한다 (ADR-0004).
 *
 * 게이트웨이는 자체 권한을 발급하지 않는다. 클라이언트가 보낸 JWT 를 downstream 9 service 가
 * 다시 검증할 수 있도록 그대로 relay 한다.
 *
 * 이 객체는 adapter-in (토큰을 컨텍스트에 넣음) 과 adapter-out (꺼내서 헤더에 실음) 이
 * 공유하는 계약이므로 application 계층에 둔다 — 어느 한 어댑터에 두면 다른 어댑터가 그것을
 * import 하느라 헥사고날 경계가 깨진다.
 *
 * 전파 경로는 Reactor Context 다. ThreadLocal 이 아닌 이유: suspend resolver 는 coroutine
 * 디스패처 위에서 돌고 downstream fan-out 은 또 다른 스레드로 갈라진다 — ThreadLocal 은 그
 * 경계를 못 넘는다. 반면 Reactor Context 는 Spring for GraphQL 의 요청 처리 전체에 전파되고,
 * `kotlinx-coroutines-reactor` 가 이를 coroutine context 로 이어준다.
 *
 * 흐름:
 *   1) adapter-in 의 TokenRelayInterceptor 가 요청 헤더의 토큰을 Reactor Context 에 [KEY] 로 넣는다.
 *   2) suspend resolver 가 그 컨텍스트를 물려받는다.
 *   3) adapter-out 어댑터가 downstream 호출 직전에 [current] 로 읽어 Authorization 헤더에 싣는다.
 */
object TokenRelay {

    /** Reactor Context 안에서 토큰을 식별하는 키. */
    const val KEY = "gateway.relay.bearer-token"

    /**
     * 현재 coroutine 의 Reactor Context 에서 relay 토큰을 읽는다. 없으면 null.
     * suspend 컨텍스트에서만 호출 가능하다 — downstream 어댑터 메서드가 모두 suspend 다.
     */
    suspend fun current(): String? {
        val reactorContext = coroutineContext[ReactorContext]?.context ?: return null
        return if (reactorContext.hasKey(KEY)) reactorContext.get<String>(KEY) else null
    }
}
