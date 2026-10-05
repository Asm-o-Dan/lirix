package com.lirix.app

import com.lirix.app.domain.Category
import com.lirix.app.domain.EventSource
import com.lirix.app.domain.EventType
import com.lirix.app.storage.Converters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testEventSourceConversion() {
        val source = EventSource.NOTIFICATION
        val stringVal = converters.fromSource(source)
        assertEquals("NOTIFICATION", stringVal)
        assertEquals(EventSource.NOTIFICATION, converters.toSource(stringVal))
    }

    @Test
    fun testEventTypeConversion() {
        val type = EventType.TRANSACTION
        val stringVal = converters.fromEventType(type)
        assertEquals("TRANSACTION", stringVal)
        assertEquals(EventType.TRANSACTION, converters.toEventType(stringVal))
    }

    @Test
    fun testCategoryConversion() {
        val category = Category.FINANCE_EXPENSE_FOOD
        val key = converters.fromCategory(category)
        assertEquals("finance.expense.food", key)
        assertEquals(Category.FINANCE_EXPENSE_FOOD, converters.toCategory(key))
    }

    @Test
    fun testVectorFloatArrayBinaryBlobConversion() {
        val testVector = floatArrayOf(0.123f, -0.456f, 0.789f, 1.0f, -1.0f)
        val blob = converters.fromFloatArray(testVector)

        assertNotNull(blob)
        assertEquals(testVector.size * 4, blob!!.size)

        val restored = converters.toFloatArray(blob)
        assertNotNull(restored)
        assertArrayEquals(testVector, restored!!, 0.00001f)
    }

    @Test
    fun testNullVectorHandling() {
        assertNull(converters.fromFloatArray(null))
        assertNull(converters.toFloatArray(null))
        assertNull(converters.toFloatArray(ByteArray(0)))
    }
}
