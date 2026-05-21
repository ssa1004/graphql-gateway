package com.example.gateway.domain

import java.util.Base64

/**
 * Relay cursor 인코딩 — offset 정수를 불투명(opaque) 문자열로 감싼다.
 *
 * GraphQL Relay 규약상 cursor 는 클라이언트가 해석하면 안 되는 불투명 값이다. 여기서는
 * 가장 단순한 형태인 `offset:<n>` 을 Base64 로 인코딩한다. downstream 이 모두 offset/limit
 * 페이징이라 이 정도면 충분하고, 추후 keyset 페이징으로 바뀌어도 cursor 표현만 교체하면 된다.
 */
object CursorCodec {

    private const val PREFIX = "offset:"

    fun encode(offset: Int): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$PREFIX$offset".toByteArray(Charsets.UTF_8))

    /**
     * cursor 를 offset 으로 디코딩한다. null 이면 0 (처음부터).
     * 형식이 깨졌으면 [IllegalArgumentException] — resolver 가 GraphQL 검증 오류로 매핑한다.
     */
    fun decode(cursor: String?): Int {
        if (cursor == null) return 0
        val decoded = try {
            String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("cursor 형식이 올바르지 않습니다: $cursor", e)
        }
        require(decoded.startsWith(PREFIX)) { "cursor 형식이 올바르지 않습니다: $cursor" }
        return decoded.removePrefix(PREFIX).toIntOrNull()
            ?: throw IllegalArgumentException("cursor 형식이 올바르지 않습니다: $cursor")
    }
}
