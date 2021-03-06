package org.jetbrains.idl2k

import org.antlr.v4.runtime.ANTLRFileStream
import java.io.File
import java.util.*

fun main(args: Array<String>) {
    val outDir = File("../../../js/js.libraries/src/generated")
    val srcDir = File("../../idl")
    if (!srcDir.exists()) {
        System.err?.println("Directory ${srcDir.absolutePath} doesn't exist")
        System.exit(1)
        return
    }

    val repositoryPre = srcDir.walkTopDown().filter { it.isDirectory || it.extension == "idl" }.asSequence().filter { it.isFile }.toList().sortedBy { it.absolutePath }.fold(Repository(emptyMap(), emptyMap(), emptyMap(), emptyMap())) { acc, e ->
        System.err.flush()
        System.err.println("Parsing ${e.absolutePath}")
        val fileRepository = parseIDL(ANTLRFileStream(e.absolutePath, "UTF-8"))

        Repository(
                interfaces = acc.interfaces.mergeReduce(fileRepository.interfaces, ::merge),
                typeDefs = acc.typeDefs + fileRepository.typeDefs,
                externals = acc.externals.merge(fileRepository.externals),
                enums = acc.enums + fileRepository.enums
        )
    }

    println("Generating...")

    val repository = repositoryPre.copy(typeDefs = repositoryPre.typeDefs.mapValues { it.value.copy(mapType(repositoryPre, it.value.types)) })

    val definitions = mapDefinitions(repository, repository.interfaces.values).map {
        if (it.name in relocations) {
            // we need this to get interfaces listed in the relocations in valid package
            // to keep compatibility with DOM Java API
            it.copy(namespace = relocations[it.name]!!)
        } else {
            it
        }
    }
    val unions = generateUnions(definitions, repository.typeDefs.values)
    val allPackages = definitions.map { it.namespace }.distinct().sorted()

    outDir.deleteRecursively()
    outDir.mkdirs()

    allPackages.forEach { pkg ->
        File(outDir, pkg + ".kt").bufferedWriter().use { w ->
            println("Generating for package $pkg...")

            w.appendln("/*")
            w.appendln(" * Generated file")
            w.appendln(" * DO NOT EDIT")
            w.appendln(" * ")
            w.appendln(" * See libraries/tools/idl2k for details")
            w.appendln(" */")

            w.appendln()
            w.appendln("package $pkg")
            w.appendln()

            allPackages.filter { it != pkg }.forEach { import ->
                w.appendln("import $import.*")
            }
            w.appendln()

            w.render(pkg, definitions, unions)
        }
    }
}

internal fun <K, V> Map<K, List<V>>.reduceValues(reduce: (V, V) -> V = { a, b -> b }): Map<K, V> = mapValues { it.value.reduce(reduce) }

internal fun <K, V> Map<K, V>.mergeReduce(other: Map<K, V>, reduce: (V, V) -> V = { a, b -> b }): Map<K, V> {
    val result = LinkedHashMap<K, V>(this.size + other.size)
    result.putAll(this)
    other.forEach { e ->
        val existing = result[e.key]

        if (existing == null) {
            result[e.key] = e.value
        }
        else {
            result[e.key] = reduce(e.value, existing)
        }
    }

    return result
}

internal fun <K, V> Map<K, List<V>>.merge(other: Map<K, List<V>>): Map<K, List<V>> {
    val result = LinkedHashMap<K, MutableList<V>>(size + other.size)
    this.forEach {
        result[it.key] = ArrayList(it.value)
    }
    other.forEach {
        val list = result[it.key]
        if (list == null) {
            result[it.key] = ArrayList(it.value)
        }
        else {
            list.addAll(it.value)
        }
    }

    return result
}