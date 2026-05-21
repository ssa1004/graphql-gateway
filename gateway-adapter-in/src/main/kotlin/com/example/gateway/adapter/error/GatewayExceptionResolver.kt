package com.example.gateway.adapter.error

import com.example.gateway.application.port.DownstreamException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.slf4j.LoggerFactory
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * GraphQL error 변환 — resolver 에서 던진 예외를 GraphQL `errors` 배열로 매핑한다 (ADR-0005).
 *
 * GraphQL 의 부분 실패 모델 핵심: 한 필드 resolver 가 실패해도 그 필드만 null 로 떨어지고
 * 나머지 데이터는 그대로 응답에 담긴다. 그래서 downstream 한 곳(billing-platform)이 죽어도
 * `order { id, invoice }` 쿼리에서 `invoice` 만 null 이 되고 `id` 는 살아 온다 (ADR-0003).
 *
 * 매핑 규칙:
 *   - [DownstreamException]      -> errorType INTERNAL_ERROR + classification DOWNSTREAM_UNAVAILABLE.
 *                                  extensions 에 어느 service 가 문제인지 표시한다.
 *   - [IllegalArgumentException] -> errorType BAD_REQUEST (잘못된 cursor 등 클라이언트 입력 오류).
 *   - 그 외                       -> errorType INTERNAL_ERROR. 내부 메시지는 클라이언트에 노출 안 함.
 *
 * downstream 장애를 INTERNAL_ERROR 로 두되 extensions 의 `classification` 으로 구분하는 이유:
 * Spring GraphQL 의 ErrorType enum 에는 503 에 해당하는 값이 없다. classification 을 보면
 * 클라이언트가 게이트웨이 버그(INTERNAL)와 downstream 일시 장애(DOWNSTREAM_UNAVAILABLE)를
 * 가려 재시도 여부를 판단할 수 있다.
 */
@Component
class GatewayExceptionResolver : DataFetcherExceptionResolverAdapter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? =
        when (ex) {
            is DownstreamException -> {
                log.warn("downstream '{}' 실패로 필드 '{}' null 처리", ex.serviceName, env.field.name)
                GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.INTERNAL_ERROR)
                    .message("downstream service '${ex.serviceName}' 를 일시적으로 사용할 수 없습니다")
                    .extensions(
                        mapOf(
                            "service" to ex.serviceName,
                            "classification" to "DOWNSTREAM_UNAVAILABLE",
                        ),
                    )
                    .build()
            }

            is IllegalArgumentException -> {
                GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(ex.message ?: "잘못된 요청입니다")
                    .extensions(mapOf("classification" to "BAD_REQUEST"))
                    .build()
            }

            else -> {
                log.error("처리되지 않은 resolver 예외 — 필드 '{}'", env.field.name, ex)
                GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.INTERNAL_ERROR)
                    .message("내부 오류가 발생했습니다")
                    .extensions(mapOf("classification" to "INTERNAL_ERROR"))
                    .build()
            }
        }
}
