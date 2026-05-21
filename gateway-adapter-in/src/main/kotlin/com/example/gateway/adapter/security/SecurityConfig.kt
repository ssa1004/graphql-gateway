package com.example.gateway.adapter.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Spring Security 설정 — JWT 검증 + GraphQL endpoint 보호 (ADR-0004).
 *
 * 게이트웨이는 OAuth2 resource server 로 동작한다. 클라이언트가 보낸 Bearer JWT 를
 * auth-service 의 JWK Set 으로 서명 검증한다. 게이트웨이 자체는 토큰을 발급하지 않는다.
 *
 * 검증된 토큰은 [TokenRelayInterceptor] 가 ThreadLocal 에 담아 downstream 호출에 그대로
 * relay 한다 (token relay) — downstream 9 service 가 같은 JWT 를 다시 검증한다.
 *
 * endpoint 정책:
 *   - /graphql            : 인증 필요 (POST). 게이트웨이의 데이터 진입점.
 *   - /graphiql           : 인증 불필요 — 개발용 GraphQL playground UI.
 *   - /actuator/health/.. : 인증 불필요 — k8s probe.
 *   - /actuator/..        : 인증 필요 — 그 외 actuator endpoint.
 *
 * 두 가지 프로필 모드:
 *   - 기본: jwt resource server. application.yml 의 issuer-uri / jwk-set-uri 로 검증.
 *   - `gateway.security.permit-all=true`: 인증을 끈다. downstream/auth-service 가 안 떠 있는
 *     로컬 데모/테스트에서 GraphQL 쿼리를 그대로 시연하기 위한 스위치 (운영 금지).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    /** 운영 기본 — JWT resource server 모드. */
    @Bean
    @ConditionalOnProperty(prefix = "gateway.security", name = ["permit-all"], havingValue = "false", matchIfMissing = true)
    fun jwtSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            // GraphQL 은 stateless — 세션을 만들지 않는다.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.GET, "/graphiql", "/graphiql/**").permitAll()
                auth.requestMatchers("/actuator/health/**").permitAll()
                auth.requestMatchers("/graphql").authenticated()
                auth.anyRequest().authenticated()
            }
            // Bearer JWT 검증 — issuer 의 JWK Set 으로 서명 확인 (application.yml 설정).
            .oauth2ResourceServer { it.jwt {} }
        return http.build()
    }

    /**
     * permit-all 모드 — 인증을 끈다. downstream/auth-service 미가용 데모/테스트 전용.
     * 운영에서는 절대 활성화하지 않는다 (`gateway.security.permit-all` 기본값 false).
     */
    @Bean
    @ConditionalOnProperty(prefix = "gateway.security", name = ["permit-all"], havingValue = "true")
    fun permitAllSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
