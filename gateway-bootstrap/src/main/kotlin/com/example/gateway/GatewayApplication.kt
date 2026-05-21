package com.example.gateway

import com.example.gateway.adapter.graphql.QueryGuardProperties
import com.example.gateway.adapter.out.config.DownstreamProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

/**
 * GraphQL gateway 의 Spring Boot 진입점.
 *
 * 9개 portfolio service 의 REST API 를 GraphQL 한 endpoint 로 묶는 facade 다 (ADR-0001).
 *
 * 컴포넌트 스캔 범위는 `com.example.gateway` 전체 — 5개 모듈(domain / application /
 * adapter-in / adapter-out / bootstrap)의 빈을 모두 잡는다. `@ConfigurationProperties`
 * 클래스는 어댑터 모듈에 흩어져 있으므로 [EnableConfigurationProperties] 로 명시 등록한다.
 */
@SpringBootApplication
@ComponentScan(basePackages = ["com.example.gateway"])
@EnableConfigurationProperties(DownstreamProperties::class, QueryGuardProperties::class)
class GatewayApplication

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
