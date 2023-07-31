package io.horizen

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{clearInvocations, times, verify, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import sparkz.core.consensus.History.ModifierIds
import sparkz.core.consensus.{HistoryReader, SyncInfo}
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.validation.RecoverableModifierError
import sparkz.core.{ModifierTypeId, PersistentNodeViewModifier, bytesToId}
import sparkz.crypto.hash.Blake2b256

import scala.util.{Failure, Random, Success}

//noinspection NotImplementedCode
class ParentIdModifiersCacheSpecification extends AnyPropSpec
  with Matchers {

  private class FakeModifier(val idValue: sparkz.util.ModifierId, val parentIdValue: sparkz.util.ModifierId) extends PersistentNodeViewModifier {
    override def parentId: sparkz.util.ModifierId = parentIdValue

    override val modifierTypeId: ModifierTypeId = ModifierTypeId @@ (0: Byte)

    override def id: sparkz.util.ModifierId = idValue

    override type M = this.type

    override def serializer: SparkzSerializer[FakeModifier.this.type] = ???
  }

  private class FakeSyncInfo extends SyncInfo {
    override def startingPoints: ModifierIds = ???

    override type M = this.type

    override def serializer: SparkzSerializer[FakeSyncInfo.this.type] = ???
  }

  property("cache should use parentId when possible and preserve order of insertion") {
    val historyReader = Mockito.mock(classOf[HistoryReader[FakeModifier, FakeSyncInfo]])
    val limit = 5
    val random = Random

    val k1 = bytesToId(Blake2b256.hash(random.nextString(5)))
    val k2 = bytesToId(Blake2b256.hash(random.nextString(5)))
    val k3 = bytesToId(Blake2b256.hash(random.nextString(5)))
    val k4 = bytesToId(Blake2b256.hash(random.nextString(5)))
    val k5 = bytesToId(Blake2b256.hash(random.nextString(5)))
    val k6 = bytesToId(Blake2b256.hash(random.nextString(5)))
    val v3 = new FakeModifier(k4, k3)
    val v4 = new FakeModifier(k5, k4)

    when(historyReader.openSurfaceIds()).thenReturn(Seq(k4))
    when(historyReader.applicableTry(any())).thenAnswer(_ => Failure(new RecoverableModifierError("Parent block IS NOT in history yet")))
    when(historyReader.applicableTry(v3)).thenAnswer(_ => Success(Unit))
    when(historyReader.applicableTry(v4)).thenAnswer(_ => Success(Unit))

    val cache = new ParentIdModifiersCache[FakeModifier, HistoryReader[FakeModifier, FakeSyncInfo]](limit)

    cache.put(k1, new FakeModifier(k2, k1))
    cache.put(k2, new FakeModifier(k3, k2))
    cache.put(k3, v3)
    cache.put(k4, v4)
    cache.put(k5, new FakeModifier(k6, k5))

    // pop first candidate that can be found by parentId
    cache.size shouldBe 5
    cache.popCandidate(historyReader) shouldBe Some(v4)
    verify(historyReader, times(1)).applicableTry(any())

    // pop second candidate that is chosen by iterating over all modifiers in the order they were inserted
    clearInvocations(historyReader)
    cache.popCandidate(historyReader) shouldBe Some(v3)
    verify(historyReader, times(3)).applicableTry(any())
    cache.size shouldBe 3

    // try pop third candidate - fail because no suitable parentId found in the cache
    clearInvocations(historyReader)
    cache.popCandidate(historyReader) shouldBe None
    verify(historyReader, times(3)).applicableTry(any())
    cache.size shouldBe 3
  }

}
