package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model

/** The model's table entry for earbuds of any brand, as the brand says it from what it knows so far. */
fun modelOf(device: Paired, state: EarbudState): Model? = Brands[device.brand].model(device, state)
