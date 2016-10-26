/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlin.serialization

public annotation class KSerializable()

public interface KOutput {
    public fun writeNamed(element: String, value: Any?)

    public fun writeNamedNotNull(element: String, value: Any) = writeNamed(element, value)
    public fun writeNamedBoolean(element: String, value: Boolean) = writeNamedNotNull(element, value)
    public fun writeNamedByte(element: String, value: Byte) = writeNamedNotNull(element, value)
    public fun writeNamedShort(element: String, value: Short) = writeNamedNotNull(element, value)
    public fun writeNamedInt(element: String, value: Int) = writeNamedNotNull(element, value)
    public fun writeNamedLong(element: String, value: Long) = writeNamedNotNull(element, value)
    public fun writeNamedFloat(element: String, value: Float) = writeNamedNotNull(element, value)
    public fun writeNamedDouble(element: String, value: Double) = writeNamedNotNull(element, value)
    public fun writeNamedChar(element: String, value: Char) = writeNamedNotNull(element, value)
    public fun writeNamedString(element: String, value: String) = writeNamedNotNull(element, value)

    public fun <T> write(obj : T, save : Save<T>) = save.save(obj, this)
}

public interface KInput {
    public fun readNamed(element: String): Any?

    public fun readNamedNotNull(element: String): Any = readNamed(element)!!
    public fun readNamedBoolean(element: String): Boolean = readNamedNotNull(element) as Boolean
    public fun readNamedByte(element: String): Byte = readNamedNotNull(element) as Byte
    public fun readNamedShort(element: String): Short = readNamedNotNull(element) as Short
    public fun readNamedInt(element: String): Int = readNamedNotNull(element) as Int
    public fun readNamedLong(element: String): Long = readNamedNotNull(element) as Long
    public fun readNamedFloat(element: String): Float = readNamedNotNull(element) as Float
    public fun readNamedDouble(element: String): Double = readNamedNotNull(element) as Double
    public fun readNamedChar(element: String): Char = readNamedNotNull(element) as Char
    public fun readNamedString(element: String): String = readNamedNotNull(element) as String

    public fun <T> read(load : Load<T>) : T = load.load(this)
}

// todo: serialization: Replace with better name
public interface Save<in T> {
    public fun save(obj : T, output: KOutput)
}

// todo: serialization: Replace with better name
public interface Load<out T> {
    public fun load(input: KInput): T
}

public interface KSerializer<T>: Save<T>, Load<T>
