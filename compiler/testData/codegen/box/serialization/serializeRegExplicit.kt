import kotlin.serialization.KInput
import kotlin.serialization.KOutput
import kotlin.serialization.KSerializable

class Out() : KOutput {
    var ok1 = 0
    var ok2 = 0
    var fail = 0

    override fun writeNamed(element: String, value: Any?) {
        when (element) {
            "value1" -> if (value == "s1") ok1++
            "value2" -> if (value == 42) ok2++
            else -> fail++
        }
    }
}

class Inp() : KInput {
    var ok1 = 0
    var ok2 = 0
    var fail = 0

    override fun readNamed(element: String): Any? {
        when (element) {
            "value1" -> { ok1++; return "s1" }
            "value2" -> { ok2++; return 42 }
            else -> { fail++; return null }
        }
    }
}

@KSerializable
class Box(p1: String, p2: Int) {
    companion object
    var value1 : String = p1
    var value2 : Int = p2
}

fun box() : String {
    val out = Out()
    out.write(Box("s1", 42), Box)
    if (out.ok1 != 1 || out.ok2 != 1 || out.fail != 0) return "Fail Out"

    val inp = Inp()
    val box = inp.read(Box)
    if (inp.ok1 != 1 || inp.ok2 != 1 || inp.fail != 0) return "Fail Inp"

    return if (box.value1 == "s1" && box.value2 == 42) "OK" else "Fail Box"
}
