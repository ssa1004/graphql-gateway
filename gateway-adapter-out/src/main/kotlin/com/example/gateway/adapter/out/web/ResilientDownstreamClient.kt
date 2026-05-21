package com.example.gateway.adapter.out.web

import com.example.gateway.application.port.DownstreamException
import com.example.gateway.application.port.TokenRelay
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.kotlin.timelimiter.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.TimeoutException

/**
 * downstream REST 호출을 Resilience4j 로 감싸는 공통 헬퍼 (ADR-0003).
 *
 * 적용 순서 (안에서 밖으로): TimeLimiter → Retry → CircuitBreaker.
 *   - TimeLimiter: 한 번의 시도가 정해진 시간을 넘으면 끊는다.
 *   - Retry: 일시적 실패(5xx / 타임아웃 / 연결 오류) 를 정해진 횟수만큼 재시도. 4xx 는 재시도 안 함.
 *   - CircuitBreaker: 실패율이 임계치를 넘으면 회로를 열어 호출 자체를 즉시 차단.
 *
 * 인스턴스 이름은 service 식별자(`auth`, `billing` ...) — application.yml 의
 * `resilience4j.*.instances.<name>` 설정과 actuator 지표가 service 단위로 분리된다.
 *
 * 호출 결과:
 *   - 200 계열: 역직렬화한 body (없으면 null).
 *   - 404: null (조회 대상 없음 — 정상 흐름, 예외 아님).
 *   - 그 외 4xx/5xx, 타임아웃, 회로 open: [DownstreamException]. resolver 의 error resolver 가
 *     GraphQL 부분 실패로 매핑한다 (ADR-0005).
 */
@Component
class ResilientDownstreamClient(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val timeLimiterRegistry: TimeLimiterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [webClient] 로 GET [uri] 를 호출하고 body 를 [T] 로 역직렬화한다.
     * 404 면 null. downstream 장애면 [DownstreamException].
     */
    suspend fun <T : Any> get(
        serviceName: String,
        webClient: WebClient,
        uri: String,
        responseType: Class<T>,
    ): T? {
        // 토큰은 suspend 컨텍스트에서 미리 읽는다 — .headers 람다는 non-suspend 다.
        val token = TokenRelay.current()
        return guarded(serviceName) {
            webClient.get()
                .uri(uri)
                .headers { headers -> token?.let { headers.setBearerAuth(it) } }
                .retrieve()
                .bodyToMono(responseType)
                .awaitSingleOrNull()
        }
    }

    /**
     * [webClient] 로 POST [uri] 를 호출한다 (body 없음 — 상태 전이용). 응답 body 를 [T] 로 역직렬화.
     */
    suspend fun <T : Any> post(
        serviceName: String,
        webClient: WebClient,
        uri: String,
        responseType: Class<T>,
    ): T? {
        val token = TokenRelay.current()
        return guarded(serviceName) {
            webClient.post()
                .uri(uri)
                .headers { headers -> token?.let { headers.setBearerAuth(it) } }
                .retrieve()
                .bodyToMono(responseType)
                .awaitSingleOrNull()
        }
    }

    /**
     * 임의의 suspend 호출 [block] 을 CB/Retry/TimeLimiter 로 감싼다.
     * 어댑터가 여러 호출을 묶어 하나의 보호 단위로 다루고 싶을 때 사용.
     *
     * 404 (WebClientResponseException.NotFound) 는 흐름상 정상이므로 회로/재시도 실패로
     * 집계하지 않고 [NotFoundSignal] 로 바꿔 즉시 정상 종료시킨다. NotFoundSignal 은
     * 보호 계층 바깥에서 다시 null 로 풀린다.
     */
    suspend fun <T> guarded(serviceName: String, block: suspend () -> T): T {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName)
        val retry = retryRegistry.retry(serviceName)
        val timeLimiter = timeLimiterRegistry.timeLimiter(serviceName)
        return try {
            circuitBreaker.executeSuspendFunction {
                retry.executeSuspendFunction {
                    timeLimiter.executeSuspendFunction {
                        try {
                            block()
                        } catch (e: WebClientResponseException.NotFound) {
                            // 404 는 정상 — 보호 계층이 실패로 세지 않도록 신호로 바꿔 던진다.
                            throw NotFoundSignal()
                        }
                    }
                }
            }
        } catch (e: NotFoundSignal) {
            @Suppress("UNCHECKED_CAST")
            null as T
        } catch (e: CallNotPermittedException) {
            log.warn("downstream '{}' 회로 open — 호출 차단", serviceName)
            throw DownstreamException(serviceName, "downstream '$serviceName' 일시 차단(circuit open)", e)
        } catch (e: TimeoutException) {
            log.warn("downstream '{}' 타임아웃", serviceName)
            throw DownstreamException(serviceName, "downstream '$serviceName' 타임아웃", e)
        } catch (e: WebClientResponseException) {
            log.warn("downstream '{}' 오류 응답 status={}", serviceName, e.statusCode)
            throw DownstreamException(serviceName, "downstream '$serviceName' 오류 응답: ${e.statusCode}", e)
        } catch (e: DownstreamException) {
            throw e
        } catch (e: Exception) {
            log.warn("downstream '{}' 호출 실패: {}", serviceName, e.message)
            throw DownstreamException(serviceName, "downstream '$serviceName' 호출 실패", e)
        }
    }
}

/**
 * retry/CircuitBreaker 안에서 404 를 정상 종료로 흘려보내기 위한 신호.
 *
 * 404 는 "조회 대상 없음" 이라는 정상 흐름이지 downstream 장애가 아니다. 그래서 회로 실패율
 * 집계에서 빠져야 한다 — application.yml 의 `resilience4j.*.instances` 가 이 타입을
 * `ignore-exceptions` 로 등록한다. (top-level public 이어야 YAML 에서 FQCN 으로 참조 가능)
 */
class NotFoundSignal : RuntimeException() {
    // 신호 전용 — 스택트레이스 불필요.
    override fun fillInStackTrace(): Throwable = this
}
