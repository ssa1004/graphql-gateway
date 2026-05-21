package com.example.gateway.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * [CursorCodec] 단위 테스트 — Relay cursor 의 인코딩 / 디코딩 왕복과 입력 방어.
 */
class CursorCodecTest {

    @Test
    fun `인코딩한 cursor 를 다시 디코딩하면 같은 offset 이 나온다`() {
        listOf(0, 1, 19, 200, 9999).forEach { offset ->
            assertThat(CursorCodec.decode(CursorCodec.encode(offset))).isEqualTo(offset)
        }
    }

    @Test
    fun `cursor 는 offset 을 그대로 노출하지 않는다 (불투명)`() {
        val cursor = CursorCodec.encode(42)
        // 클라이언트가 cursor 에서 숫자를 바로 읽어내지 못해야 한다.
        assertThat(cursor).doesNotContain("42")
    }

    @Test
    fun `null cursor 는 offset 0 으로 디코딩된다`() {
        assertThat(CursorCodec.decode(null)).isEqualTo(0)
    }

    @Test
    fun `형식이 깨진 cursor 는 예외를 던진다`() {
        assertThatThrownBy { CursorCodec.decode("not-a-valid-cursor!!!") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `prefix 가 없는 Base64 문자열은 예외를 던진다`() {
        val notACursor = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("hello".toByteArray())
        assertThatThrownBy { CursorCodec.decode(notACursor) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
