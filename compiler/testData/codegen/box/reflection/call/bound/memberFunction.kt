// TODO: investigate should it be ran for JS or not
// IGNORE_BACKEND: JS

// WITH_REFLECT

import kotlin.reflect.*

class C(val k: String) {
    fun foo(s: String) = s + k
}

fun box(): String =
        C("K")::foo.call("O")

