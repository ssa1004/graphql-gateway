package com.example.gateway.adapter.graphql

import com.example.gateway.domain.Connection
import com.example.gateway.domain.PageInfo

/**
 * GraphQL Relay connection 의 응답 표현.
 *
 * 도메인의 [Connection] 은 제네릭이지만, GraphQL schema 의 `SearchConnection` /
 * `SecurityAlertConnection` 은 구체 타입이다. Spring for GraphQL 은 필드 이름으로 매핑하므로
 * (`edges` / `pageInfo` / `totalCount`), 제네릭 [GqlConnection] 한 벌로 두 schema 타입을
 * 모두 만족시킬 수 있다 — 필드 이름만 schema 와 맞으면 된다.
 *
 * 이 변환을 adapter-in 에 두는 이유: cursor connection 은 GraphQL/Relay 표현 규약이고,
 * 도메인은 그것을 몰라야 한다 (헥사고날 경계).
 */
data class GqlConnection<T>(
    val edges: List<GqlEdge<T>>,
    val pageInfo: PageInfo,
    val totalCount: Int,
)

data class GqlEdge<T>(
    val cursor: String,
    val node: T,
)

/** 도메인 [Connection] -> GraphQL 응답 표현. */
fun <T> Connection<T>.toGql(): GqlConnection<T> = GqlConnection(
    edges = edges.map { GqlEdge(cursor = it.cursor, node = it.node) },
    pageInfo = pageInfo,
    totalCount = totalCount,
)
