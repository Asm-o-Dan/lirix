package com.example.npc.core.model.normalize

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventNormalizerCleanTextTest {

    @Test
    fun `cleanText returns empty string for null, empty or blank input`() {
        assertEquals("", EventNormalizer.cleanText(null))
        assertEquals("", EventNormalizer.cleanText(""))
        assertEquals("", EventNormalizer.cleanText("   "))
        assertEquals("", EventNormalizer.cleanText("\t \u00A0 \r\n  "))
    }

    @Test
    fun `cleanText strips invisible Unicode characters`() {
        val invisibleChars = "\u200B\u200C\u200D\uFEFF\u00AD"
        assertEquals("", EventNormalizer.cleanText(invisibleChars))

        val textWithInvisibles = "Сбер\u200BБанк:\u200C 500\u200D ₽\uFEFF поступило\u00ADна карту"
        val expected = "СберБанк: 500 ₽ поступилона карту"
        assertEquals(expected, EventNormalizer.cleanText(textWithInvisibles))
    }

    @Test
    fun `cleanText unifies line breaks and collapses 3 or more line breaks into 2`() {
        val windowsLineBreaks = "Line 1\r\n\r\n\r\n\r\nLine 2"
        assertEquals("Line 1\n\nLine 2", EventNormalizer.cleanText(windowsLineBreaks))

        val macClassicLineBreaks = "Line 1\r\r\rLine 2"
        assertEquals("Line 1\n\nLine 2", EventNormalizer.cleanText(macClassicLineBreaks))

        val unixLineBreaks = "Line 1\n\n\n\n\nLine 2"
        assertEquals("Line 1\n\nLine 2", EventNormalizer.cleanText(unixLineBreaks))

        val preserveParagraphs = "Paragraph 1\n\nParagraph 2"
        assertEquals("Paragraph 1\n\nParagraph 2", EventNormalizer.cleanText(preserveParagraphs))
    }

    @Test
    fun `cleanText collapses horizontal whitespaces and trims individual lines`() {
        val input = "Order\t\t#12345\u00A0\u00A0\u00A0Status:\tDelivered"
        val expected = "Order #12345 Status: Delivered"
        assertEquals(expected, EventNormalizer.cleanText(input))

        val multilineWithSpaces = "   Title   \n   Description with   many   spaces   \n   Footer   "
        val expectedMultiline = "Title\nDescription with many spaces\nFooter"
        assertEquals(expectedMultiline, EventNormalizer.cleanText(multilineWithSpaces))
    }

    @Test
    fun `cleanText handles realistic bank and messenger notifications`() {
        val bankNotification = "  Перевод   получен:   500 ₽.  \r\n\r\n\r\nОт: Иван И.\u200B  "
        val expectedBank = "Перевод получен: 500 ₽.\n\nОт: Иван И."
        assertEquals(expectedBank, EventNormalizer.cleanText(bankNotification))

        val otpNotification = " \uFEFF Ваш проверочный код: 492019 \r\n Никому не сообщайте! \n "
        val expectedOtp = "Ваш проверочный код: 492019\nНикому не сообщайте!"
        assertEquals(expectedOtp, EventNormalizer.cleanText(otpNotification))
    }

    @Test
    fun `cleanText removes leading and trailing empty lines from result`() {
        val input = "\n\n\r\n   Header   \n\n   Body   \n\n\r\n"
        val expected = "Header\n\nBody"
        assertEquals(expected, EventNormalizer.cleanText(input))
    }
}
