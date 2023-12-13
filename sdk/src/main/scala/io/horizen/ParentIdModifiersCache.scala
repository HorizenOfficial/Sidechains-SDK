package io.horizen

import sparkz.core.consensus.HistoryReader
import sparkz.core.validation.RecoverableModifierError
import sparkz.core.{DefaultModifiersCache, PersistentNodeViewModifier}

import scala.collection.mutable
import scala.util.{Failure, Success}

class ParentIdModifiersCache[PMOD <: PersistentNodeViewModifier, HR <: HistoryReader[PMOD, _]](override val maxSize: Int)
  extends DefaultModifiersCache[PMOD, HR](maxSize) {

  protected val parentIdCache: mutable.Map[K, V] = mutable.Map[K, V]()

  override def popCandidate(history: HR): Option[V] = {
    // get bestBlockId from history and check if there is a modifier with this parentId in cache
    val maybeParentId: Option[K] = history.openSurfaceIds().collectFirst { case id if parentIdCache.contains(id) => id }

    val maybeK = maybeParentId.flatMap { parentId =>
      // current tip in cache, check the modifier. (now this just checks that parentId is in history)
      val modifier = parentIdCache(parentId)
      history.applicableTry(modifier) match {
        case Failure(e) if e.isInstanceOf[RecoverableModifierError] =>
          // do nothing - modifier may be applied in future
          None
        case Failure(e) =>
          // non-recoverable error - remove modifier from cache
          log.warn(s"Modifier ${modifier.encodedId} became permanently invalid and will be removed from cache", e)
          remove(modifier.id)
          None
        case Success(_) =>
          remove(modifier.id)
      }
    }

    maybeK.orElse {
      // current tip not in cache, maybe fork occurred, scan all modifiers
      findCandidateKey(history).flatMap(k => remove(k))
    }
  }

  override def put(key: K, value: V): Unit = {
    if (!contains(key)) {
      cache.put(key, value)
      parentIdCache.put(value.parentId, value)
      onPut(key)
    }
  }

  override def remove(key: K): Option[V] = {
    cache.remove(key).map { removed =>
      parentIdCache.remove(removed.parentId)
      onRemove(key)
      removed
    }
  }
}
