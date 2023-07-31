package io.horizen

import sparkz.core.consensus.HistoryReader
import sparkz.core.validation.RecoverableModifierError
import sparkz.core.{DefaultModifiersCache, PersistentNodeViewModifier}

import scala.collection.mutable
import scala.util.{Failure, Success}

class ParentIdModifiersCache[PMOD <: PersistentNodeViewModifier, HR <: HistoryReader[PMOD, _]](override val maxSize: Int)
  extends DefaultModifiersCache[PMOD, HR](maxSize) {

  override protected val cache: mutable.Map[K, V] = mutable.LinkedHashMap[K, V]()

  override def popCandidate(history: HR): Option[V] = {
    // get bestBlockId from history and check if there is a modifier with this parentId in cache
    val maybeParentId: Option[K] = history.openSurfaceIds().collectFirst { case id if contains(id) => id }

    val maybeK = maybeParentId.filter { parentId =>
      // current tip in cache, check the modifier. (now this just checks that parentId is in history)
      val modifier = cache(parentId)
      history.applicableTry(modifier) match {
        case Failure(e) if e.isInstanceOf[RecoverableModifierError] =>
          // do nothing - modifier may be applied in future
          false
        case Failure(e) =>
          // non-recoverable error - remove modifier from cache
          log.warn(s"Modifier ${modifier.encodedId} became permanently invalid and will be removed from cache", e)
          remove(modifier.parentId)
          false
        case Success(_) =>
          true
      }
    }
    val candidateModifierKey = maybeK.orElse {
      // current tip not in cache, maybe fork occurred, scan all modifiers
      findCandidateKey(history)
    }

    candidateModifierKey.flatMap(k => remove(k))
  }
}
