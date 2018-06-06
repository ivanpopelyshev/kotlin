@file:Suppress("NOTHING_TO_INLINE")
package kotlin

@kotlin.internal.InlineOnly
inline fun assert(value: Boolean) {
    if (!value) {
        throw AssertionError("Assertion Failed")
    }
}

@kotlin.internal.InlineOnly
inline fun assert(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        val message = lazyMessage()
        throw AssertionError(message)
    }
}
