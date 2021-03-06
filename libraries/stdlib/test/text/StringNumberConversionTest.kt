@file:kotlin.jvm.JvmVersion
package test.text

import kotlin.test.*
import org.junit.Test

class StringNumberConversionTest {

    @Test fun toBoolean() {
        assertEquals(true, "true".toBoolean())
        assertEquals(true, "True".toBoolean())
        assertEquals(false, "false".toBoolean())
        assertEquals(false, "not so true".toBoolean())
    }

    @Test fun toByte() {
        compareConversion({it.toByte()}, {it.toByteOrNull()}) {
            assertProduces("127", Byte.MAX_VALUE)
            assertProduces("+77", 77.toByte())
            assertProduces("-128", Byte.MIN_VALUE)
            assertFailsOrNull("128")
        }

        compareConversionWithRadix(String::toByte, String::toByteOrNull) {
            assertProduces(16, "7a", 0x7a.toByte())
            assertProduces(16, "+7F", 127.toByte())
            assertProduces(16, "-80", (-128).toByte())
            assertFailsOrNull(2, "10000000")
        }
    }

    @Test fun toShort() {
        compareConversion({it.toShort()}, {it.toShortOrNull()}) {
            assertProduces("+77", 77.toShort())
            assertProduces("32767", Short.MAX_VALUE)
            assertProduces("-32768", Short.MIN_VALUE)
            assertFailsOrNull("+32768")
        }

        compareConversionWithRadix(String::toShort, String::toShortOrNull) {
            assertProduces(16, "7FFF", 0x7FFF.toShort())
            assertProduces(16, "-8000", (-0x8000).toShort())
            assertFailsOrNull(5, "10000000")
        }
    }

    @Test fun toInt() {
        compareConversion({it.toInt()}, {it.toIntOrNull()}) {
            assertProduces("77", 77)
            assertProduces("+2147483647", Int.MAX_VALUE)
            assertProduces("-2147483648", Int.MIN_VALUE)

            assertFailsOrNull("2147483648")
            assertFailsOrNull("-2147483649")
            assertFailsOrNull("239239kotlin")
        }

        compareConversionWithRadix(String::toInt, String::toIntOrNull) {
            assertProduces(10, "0", 0)
            assertProduces(10, "473", 473)
            assertProduces(10, "+42", 42)
            assertProduces(10, "-0", 0)
            assertProduces(10, "2147483647", 2147483647)
            assertProduces(10, "-2147483648", -2147483648)

            assertProduces(16, "-FF", -255)
            assertProduces(16, "-ff", -255)
            assertProduces(2, "1100110", 102)
            assertProduces(27, "Kona", 411787)

            assertFailsOrNull(10, "2147483648")
            assertFailsOrNull(8, "99")
            assertFailsOrNull(10, "Kona")
        }

        assertFailsWith<IllegalArgumentException>("Expected to fail with radix 1") { "1".toInt(radix = 1) }
        assertFailsWith<IllegalArgumentException>("Expected to fail with radix 37") { "37".toIntOrNull(radix = 37) }
    }

    @JvmVersion
    @Test fun toIntArabicDigits() {
        compareConversion({ it.toInt() }, { it.toIntOrNull() }) {
            assertProduces("٢٣١٩٦٠", 231960)
        }
    }

    @Test fun toLong() {
        compareConversion({it.toLong()}, {it.toLongOrNull()}) {
            assertProduces("77", 77.toLong())
            assertProduces("+9223372036854775807", Long.MAX_VALUE)
            assertProduces("-9223372036854775808", Long.MIN_VALUE)

            assertFailsOrNull("9223372036854775808")
            assertFailsOrNull("-9223372036854775809")
            assertFailsOrNull("922337 75809")
            assertFailsOrNull("92233,75809")
            assertFailsOrNull("92233`75809")
            assertFailsOrNull("-922337KOTLIN775809")
        }

        compareConversionWithRadix(String::toLong, String::toLongOrNull) {
            assertProduces(10, "0", 0L)
            assertProduces(10, "473", 473L)
            assertProduces(10, "+42", 42L)
            assertProduces(10, "-0", 0L)

            assertProduces(16, "7F11223344556677", 0x7F11223344556677)
            assertProduces(16, "+7faabbccddeeff00", 0x7faabbccddeeff00)
            assertProduces(16, "-8000000000000000", Long.MIN_VALUE)
            assertProduces(2, "1100110", 102L)
            assertProduces(36, "Hazelnut", 1356099454469L)

            assertFailsOrNull(8, "99")
            assertFailsOrNull(10, "Hazelnut")
        }

        assertFailsWith<IllegalArgumentException>("Expected to fail with radix 37") { "37".toLong(radix = 37) }
        assertFailsWith<IllegalArgumentException>("Expected to fail with radix 1") { "1".toLongOrNull(radix = 1) }
    }

    @JvmVersion
    @Test fun toLongArabicDigits() {
        compareConversion({ it.toLong() }, { it.toLongOrNull() }) {
            assertProduces("٢٣١٩٦٠٧٧٨٤٥٩", 231960778459)
        }
    }

    @Test fun toFloat() {
        compareConversion(String::toFloat, String::toFloatOrNull) {
            assertProduces("77.0", 77.0f)
            assertProduces("-1e39", Float.NEGATIVE_INFINITY)
            assertProduces("1000000000000000000000000000000000000000", Float.POSITIVE_INFINITY)
            assertFailsOrNull("dark side")
        }
    }

    @Test fun toDouble() {
        compareConversion(String::toDouble, String::toDoubleOrNull) {
            assertProduces("-77", -77.0)
            assertProduces("77.", 77.0)
            assertProduces("77.0", 77.0)
            assertProduces("-1.77", -1.77)
            assertProduces("+.77", 0.77)
            assertProduces("\t-77 \n", -77.0)
            assertProduces("7.7e1", 77.0)
            assertProduces("+770e-1", 77.0)

            assertProduces("-NaN", -Double.NaN)
            assertProduces("+Infinity", Double.POSITIVE_INFINITY)
            assertProduces("0x77p1", (0x77 shl 1).toDouble())
            assertProduces("0x.77P8", 0x77.toDouble())

            assertFailsOrNull("7..7")
            assertFailsOrNull("0x77e1")
            assertFailsOrNull("007 not a number")
        }
    }

    @Test fun byteToStringWithRadix() {
        assertEquals("7a", 0x7a.toByte().toString(16))
        assertEquals("-80", Byte.MIN_VALUE.toString(radix = 16))
        assertEquals("3v", Byte.MAX_VALUE.toString(radix = 32))
        assertEquals("-40", Byte.MIN_VALUE.toString(radix = 32))
        // TODO: IllegalArgumentException on incorrect radix
    }

    @Test fun shortToStringWithRadix() {
        assertEquals("7FFF", 0x7FFF.toShort().toString(radix = 16).toUpperCase())
        assertEquals("-8000", (-0x8000).toShort().toString(radix = 16))
        assertEquals("-sfs", (-29180).toShort().toString(radix = 32))

        // TODO: IllegalArgumentException on incorrect radix
    }

    @Test fun intToStringWithRadix() {
        assertEquals("-ff", (-255).toString(radix = 16))
        assertEquals("1100110", 102.toString(radix = 2))
        assertEquals("kona", 411787.toString(radix = 27))
        // TODO: IllegalArgumentException on incorrect radix

    }

    @Test fun longToStringWithRadix() {
        assertEquals("7f11223344556677", 0x7F11223344556677.toString(radix = 16))
        assertEquals("hazelnut", 1356099454469L.toString(radix = 36))
        assertEquals("-8000000000000000", Long.MIN_VALUE.toString(radix = 16))

        // TODO: IllegalArgumentException on incorrect radix
//        assertFailsWith<IllegalArgumentException>("Expected to fail with radix 37") { 37.toString(radix = 37) }
//        assertFailsWith<IllegalArgumentException>("Expected to fail with radix 1") { 1.toString(radix = 1) }
    }
}


private fun <T : Any> compareConversion(convertOrFail: (String) -> T,
                                        convertOrNull: (String) -> T?,
                                        assertions: ConversionContext<T>.() -> Unit) {
    ConversionContext(convertOrFail, convertOrNull).assertions()
}


private fun <T : Any> compareConversionWithRadix(convertOrFail: String.(Int) -> T,
                                                 convertOrNull: String.(Int) -> T?,
                                                 assertions: ConversionWithRadixContext<T>.() -> Unit) {
    ConversionWithRadixContext(convertOrFail, convertOrNull).assertions()
}


private class ConversionContext<T: Any>(val convertOrFail: (String) -> T,
                                        val convertOrNull: (String) -> T?) {
    fun assertProduces(input: String, output: T) {
        assertEquals(output, convertOrFail(input.removeLeadingPlusOnJava6()))
        assertEquals(output, convertOrNull(input))
    }

    fun assertFailsOrNull(input: String) {
        assertFailsWith<NumberFormatException>("Expected to fail on input \"$input\"") { convertOrFail(input) }
        assertNull(convertOrNull(input), message = "On input \"$input\"")
    }
}

private class ConversionWithRadixContext<T: Any>(val convertOrFail: String.(Int) -> T,
                                                 val convertOrNull: String.(Int) -> T?) {
    fun assertProduces(radix: Int, input: String, output: T) {
        assertEquals(output, input.removeLeadingPlusOnJava6().convertOrFail(radix))
        assertEquals(output, input.convertOrNull(radix))
    }

    fun assertFailsOrNull(radix: Int, input: String) {
        assertFailsWith<NumberFormatException>("Expected to fail on input \"$input\" with radix $radix",
                                               { input.convertOrFail(radix) })

        assertNull(input.convertOrNull(radix), message = "On input \"$input\" with radix $radix")
    }
}

private val isJava6 = System.getProperty("java.version").startsWith("1.6.")

private fun String.removeLeadingPlusOnJava6(): String =
        if (isJava6) removePrefix("+") else this