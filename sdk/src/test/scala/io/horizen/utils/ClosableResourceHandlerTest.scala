package io.horizen.utils

import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar


class ClosableResourceHandlerTest extends JUnitSuite with MockitoSugar {


  @Test
  def closeTest(): Unit = {
    val resourceHandler = new ClosableResourceHandler {}
    val mockResource = mock[AutoCloseable]

    //Test: Everything is fine => close is called
    resourceHandler.using(mockResource){resource =>
      "Whatever"
    }
    verify(mockResource, times(1)).close()

  }

  @Test
  def returnStatementTest(): Unit = {
    val resourceHandler = new ClosableResourceHandler {}
    val mockResource = mock[AutoCloseable]

    //Test with return statement in function literal=> close is not called
    resourceHandler.using(mockResource) { resource =>
      return "Avoid using return in function literals"
    }
    verify(mockResource, times(0)).close()
  }

  @Test
  def exceptionTest(): Unit = {
    val resourceHandler = new ClosableResourceHandler {}
    val mockResource = mock[AutoCloseable]

    //Test with exception in function literal => close is called

    assertThrows[MyException](classOf[MyException],resourceHandler.using(mockResource) { resource =>
      throw new MyException()
    })
    verify(mockResource, times(1)).close()
  }


  @Test
  def exceptionDuringCloseTest(): Unit = {
    val resourceHandler = new ClosableResourceHandler {}
    val mockResource = mock[AutoCloseable]

    //Test with exception during close()
    Mockito.when(mockResource.close()).thenThrow(new MyCloseException)
    assertThrows[MyCloseException]( classOf[MyCloseException],resourceHandler.using(mockResource) { resource =>
      "So far, so good"
    })
    verify(mockResource, times(1)).close()

  }


  @Test
  def doubleExceptionTest(): Unit = {
    val resourceHandler = new ClosableResourceHandler {}
    val mockResource = mock[AutoCloseable]

    //Test  with exception in function literal and during close => close() exception masks the first exception
    Mockito.when(mockResource.close()).thenThrow(new MyCloseException)
    assertThrows[MyCloseException](classOf[MyCloseException], resourceHandler.using(mockResource) { resource =>
      throw new MyException()
    })
    verify(mockResource, times(1)).close()

  }


  @Test
  def nullResourceTest(): Unit = {
    val resourceHandler = new ClosableResourceHandler {}
    val mockResource = mock[AutoCloseable]

    //Test with null resource => close() is not called and NullPointerException is not thrown
    resourceHandler.using(null) { resource =>
      null
    }
  }


  class MyException extends Exception
  class MyCloseException extends Exception

}
