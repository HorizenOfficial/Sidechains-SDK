package com.horizen.utils

import scorex.core.{DefaultModifiersCache, PersistentNodeViewModifier}
import scorex.core.consensus.HistoryReader


/**
 * A cache which is storing persistent modifiers not applied to history yet.
 *
 * This trait is not thread-save so it should be used only as a local field of an actor
 * and its methods should not be called from lambdas, Future, Future.map, etc.
 *
 * @tparam PMOD - type of a persistent node view modifier (or a family of modifiers).
 */

class SDKModifiersCache[PMOD <: PersistentNodeViewModifier, H <: HistoryReader[PMOD, _]] (override val maxSize: Int)
  extends DefaultModifiersCache[PMOD, H] (maxSize) {
  @Override
  override def put(key: K, value: V): Unit = {
    if (!contains(key)) {
      cache.put(key, value)
      onPut(key)
    }
  }
}
