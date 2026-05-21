package com.example.gateway.adapter.graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.instrumentation.Instrumentation
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 쿼리 complexity / depth 제한 — GraphQL DoS 방어 (ADR-0008).
 *
 * GraphQL 은 클라이언트가 쿼리 모양을 정하므로, 깊게 중첩되거나 필드가 폭증하는 쿼리 하나가
 * 게이트웨이와 9개 downstream 을 한꺼번에 끌어내릴 수 있다. 이를 막는 두 가지 정적 한계를 둔다:
 *
 *   - depth     : 쿼리 트리의 최대 중첩 깊이. `a { b { c { ... } } }` 의 과도한 중첩을 막는다.
 *   - complexity: 필드 수에 가중치를 합산한 비용. 넓게 퍼지는 쿼리(필드 폭증)를 막는다.
 *
 * graphql-java 의 [MaxQueryDepthInstrumentation] / [MaxQueryComplexityInstrumentation] 을
 * Instrumentation 빈으로 등록하면 Spring for GraphQL 이 자동으로 실행 파이프라인에 끼운다.
 * 한계를 넘는 쿼리는 실행 전에 거부된다 (downstream 호출 0).
 *
 * 한계값은 `gateway.query-guard.*` 로 조정 가능 — 실제 클라이언트 쿼리 모양을 보고 조정한다.
 */
@Configuration(proxyBeanMethods = false)
class QueryGuardConfig {

    @Bean
    fun maxQueryDepthInstrumentation(props: QueryGuardProperties): Instrumentation =
        MaxQueryDepthInstrumentation(props.maxDepth)

    @Bean
    fun maxQueryComplexityInstrumentation(props: QueryGuardProperties): Instrumentation =
        MaxQueryComplexityInstrumentation(props.maxComplexity)
}

/**
 * 쿼리 가드 한계값.
 *
 * 기본값은 게이트웨이 schema 의 정상 쿼리(조인 2~3단계)를 넉넉히 허용하면서, 명백히
 * 비정상인 쿼리는 거르는 선이다. depth 15 / complexity 200.
 */
@ConfigurationProperties(prefix = "gateway.query-guard")
data class QueryGuardProperties(
    val maxDepth: Int = 15,
    val maxComplexity: Int = 200,
)
