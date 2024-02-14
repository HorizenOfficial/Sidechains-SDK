package io.horizen.account.state

import io.horizen.evm.{Address, Hash}
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.crypto.hash.Blake2b256

import java.math.BigInteger

class StateDbArrayTest
  extends JUnitSuite
  with MessageProcessorFixture {

  val ACCOUNT: Address = new Address("0x00000000000000000000deadbeefaaaa22222222")
  @Test
  def testStateDbArray(): Unit = {
    usingView { view =>
      createSenderAccount(view, BigInteger.TEN, ACCOUNT)

      val array = new StateDbArray(ACCOUNT, "Whatever".getBytes("UTF-8"))
      assertEquals(0, array.getSize(view))

      assertArrayEquals(Hash.ZERO.toBytes, array.getValue(view, 1))
      var ex = intercept[IllegalArgumentException] {
        array.getValue(view, -1)
      }
      assertTrue(ex.getMessage.contains("Index cannot be negative"))

      ex = intercept[IllegalArgumentException] {
        array.removeAndRearrange(view, -1)
      }
      assertTrue(ex.getMessage.contains("Index cannot be negative"))

      ex = intercept[IllegalArgumentException] {
        array.removeAndRearrange(view, 0)
      }
      assertTrue(ex.getMessage.contains("Index out of range"))

      val initialValue = Blake2b256.hash("Some value")
      ex = intercept[IllegalArgumentException] {
        array.updateValue(view, -1, initialValue)
      }
      assertTrue(ex.getMessage.contains("Index cannot be negative"))

      ex = intercept[IllegalArgumentException] {
        array.updateValue(view, 0, initialValue)
      }
      assertTrue(ex.getMessage.contains("Index out of range"))

      // Test append
      var index = array.append(view, initialValue)
      assertEquals("Wrong index", 0, index)
      assertEquals("Wrong size", 1, array.getSize(view))

      assertArrayEquals("Wrong value", initialValue, array.getValue(view, index))

      val anotherValue = Blake2b256.hash("Another value")
      array.updateValue(view, index, anotherValue)
      assertArrayEquals("Wrong value", anotherValue, array.getValue(view, index))

      var lastValue = array.removeAndRearrange(view, index)
      assertArrayEquals("Wrong value", anotherValue, lastValue)
      assertEquals("Wrong size", 0, array.getSize(view))

      assertArrayEquals(Hash.ZERO.toBytes, array.getValue(view, index))

      ex = intercept[IllegalArgumentException] {
        array.updateValue(view, index, initialValue)
      }
      assertTrue(ex.getMessage.contains("Index out of range"))

      var size = 10
      (0 until size).foreach{
        i =>
          var index = array.append(view, Blake2b256.hash(s"Another value $i"))
          assertEquals("Wrong index", i, index)
      }

      assertEquals("Wrong size", size, array.getSize(view))
      assertArrayEquals(Blake2b256.hash("Another value 3"), array.getValue(view, 3))

      // Remove last elem of the array
      var indexToRemove = size - 1
      lastValue = array.removeAndRearrange(view, indexToRemove)
      assertArrayEquals("Wrong value", Blake2b256.hash(s"Another value $indexToRemove"), lastValue)
      size =  size - 1
      assertEquals("Wrong size", size, array.getSize(view))

      assertArrayEquals(Hash.ZERO.toBytes, array.getValue(view, indexToRemove))

      (0 until size).foreach{
        i =>
          assertArrayEquals(Blake2b256.hash(s"Another value $i"), array.getValue(view, i))
      }

      // Remove first elem of the array. The last elem of the array will be moved in the position 0
      indexToRemove = 0
      lastValue = array.removeAndRearrange(view, indexToRemove)
      assertArrayEquals("Wrong value", Blake2b256.hash(s"Another value ${size - 1}"), lastValue)
      assertEquals("Wrong size", size - 1, array.getSize(view))
      assertArrayEquals(Hash.ZERO.toBytes, array.getValue(view, size - 1))

      // Check that in this case the last element of the array was moved to the position of the deleted element
      assertArrayEquals("The last value was not moved to the deleted elem position", lastValue, array.getValue(view, indexToRemove))
      size =  size - 1
      // the other elements are unaffected
      (1 until size).foreach{
        i =>
          assertArrayEquals(Blake2b256.hash(s"Another value $i"), array.getValue(view, i))
      }
      val firstMovedElem = lastValue
      // Remove an elem in the middle of the array. The last elem of the array will be moved in its position
      indexToRemove = size / 2
      lastValue = array.removeAndRearrange(view, indexToRemove)
      assertArrayEquals("Wrong value", Blake2b256.hash(s"Another value ${size - 1}"), lastValue)
      assertEquals("Wrong size", size - 1, array.getSize(view))
      assertArrayEquals(Hash.ZERO.toBytes, array.getValue(view, size - 1))

      size =  size - 1
      assertArrayEquals(firstMovedElem, array.getValue(view, 0))
      (1 until indexToRemove).foreach{
        i =>
          assertArrayEquals(Blake2b256.hash(s"Another value $i"), array.getValue(view, i))
      }
      assertArrayEquals("The last value was not moved to the deleted elem position", lastValue, array.getValue(view, indexToRemove))
      (indexToRemove + 1 until size).foreach{
        i =>
          assertArrayEquals(Blake2b256.hash(s"Another value $i"), array.getValue(view, i))
      }

      // Append a new value
      val newValue = Blake2b256.hash(s"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife'")
      index = array.append(view, newValue)
      assertEquals("Wrong index", size, index)
      assertEquals("Wrong size", size + 1, array.getSize(view))
      assertArrayEquals(newValue, array.getValue(view, index))

      //Remove all the elements

      (0 to size).foreach{_ => array.removeAndRearrange(view, 0)}

      assertEquals("Wrong size", 0, array.getSize(view))
      (0 to size).foreach{
        i =>
          assertArrayEquals(Hash.ZERO.toBytes,array.getValue(view, i))
      }

    }
  }

}
