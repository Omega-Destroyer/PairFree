package io.github.omegadestroyer.pairfree.spike

import io.github.omegadestroyer.pairfree.protocol.Identify

/** Step one of the spike: the protocol code compiled for Windows answers a question from its own tables. */
fun main() {
    println("PairFree spike: brands known to Identify = ${io.github.omegadestroyer.pairfree.protocol.Maker.entries.size}")
    println(Identify::class.simpleName)
}
