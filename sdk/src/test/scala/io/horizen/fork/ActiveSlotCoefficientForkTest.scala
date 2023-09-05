package io.horizen.fork

import org.junit.Assert.fail
import org.junit.Test
import org.scalatest.Assertions.assertThrows

class ActiveSlotCoefficientForkTest {
  @Test
  def ifWrongCoefficientArgument_throwAnIllegalArgumentException(): Unit = {
    // Arrange
    val illegalArguments = Seq(-2, -10, 2, 10, -3.5, 7.77)

    // Act && Assert
    illegalArguments.foreach(arg => assertThrows[IllegalArgumentException](ActiveSlotCoefficientFork(arg)))
  }

  @Test
  def ifCorrectCoefficientArgument_theCaseClassIsInstantiated(): Unit = {
    // Arrange
    val legalArguments = Seq(-1, 0.1, 0.3, 0.1234, 0.9999999, 1)

    // Act && Assert
    legalArguments.foreach(arg => try {
      ActiveSlotCoefficientFork(arg)
    } catch {
      case _: Throwable => fail("Unexpected ifCorrectCoefficientArgument_theCaseClassIsInstantiated case test failed")
    })
  }
}
