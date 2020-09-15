import java.io.File
import java.util
import java.util.Random

import cats.instances.int
import com.horizen.librustsidechains.FieldElement
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Ignore, Test}

import scala.collection.JavaConverters._
import com.horizen.merkletreenative.{BigLazyMerkleTree, BigMerkleTree}

class ZendooScMerkleTreeTest extends TempDirectoriesCreator {
  implicit class LongWithBound(rnd: Random) {
    def nextLong(bound: Long): Long = {
      if (bound <= 0) throw new IllegalArgumentException()
      rnd.nextLong() % bound
    }
  }

  private val freePositionCheckCount = 12
  private val rnd: Random = new Random(42)

  private def createFiledElements(count: Int): List[FieldElement] = {
    (1 to count).map(_ => FieldElement.createRandom()).toList
  }


  private def calculatePositionToElement(merkleTree: BigLazyMerkleTree, fieldElementsInTree: List[FieldElement]): Map[Long, FieldElement] = {
    fieldElementsInTree.map(element => (merkleTree.getPosition(element), element)).toMap
  }

  private def checkTree(merkleTree: BigLazyMerkleTree, fieldElementsInTree: List[FieldElement])(implicit merkleTreeHeight: Int): Unit = {
    val positionToElement = calculatePositionToElement(merkleTree, fieldElementsInTree)
    val usedPositions = positionToElement.keySet
    val merkleTreeMaxPosition: Long = 1 << merkleTreeHeight - 1


    val freePositions = (1 to freePositionCheckCount).map(_ => Math.abs(rnd.nextLong(merkleTreeMaxPosition))).filter(!usedPositions.contains(_))
    freePositions.foreach{freePosition =>
      assert(merkleTree.isPositionEmpty(freePosition), "position expected to be empty")
    }

    usedPositions.foreach{usedPosition =>
      assert(!merkleTree.isPositionEmpty(usedPosition), "position expected to be used")
    }
  }

  @Test
  def simpleTest(): Unit = {
    val checkedElementsCount = 11
    implicit val merkleTreeHeight: Int = 10
    val merkleTreeStatePath = createNewTempDirPath().getAbsolutePath + File.separator + "state"
    println(merkleTreeStatePath)
    val dbPath = createNewTempDirPath().getAbsolutePath
    val cachePath = createNewTempDirPath().getAbsolutePath

    //all paths shall not exist in case if new tree is created
    val merkleTree = BigLazyMerkleTree.init(merkleTreeHeight, merkleTreeStatePath, dbPath, cachePath)
    checkTree(merkleTree, List())

    val fieldElements = createFiledElements(checkedElementsCount)
    merkleTree.addLeaves(fieldElements.asJava)
    checkTree(merkleTree, fieldElements)
    val firstRoot = merkleTree.root()

    val positionToDelete = fieldElements.tail.map(fieldElement => merkleTree.getPosition(fieldElement))

    //remove leaves from the tree check new status
    merkleTree.removeLeaves(positionToDelete.toArray)
    checkTree(merkleTree, List(fieldElements.head))
    positionToDelete.foreach{freePosition =>
      assert(merkleTree.isPositionEmpty(freePosition), "position expected to be empty")
    }
    assert(!util.Arrays.equals(merkleTree.root().serializeFieldElement(), firstRoot.serializeFieldElement()))

    merkleTree.addLeaves(fieldElements.tail.asJava)
    checkTree(merkleTree, fieldElements) //not deleted head + recently added tail
    assert(util.Arrays.equals(merkleTree.root().serializeFieldElement(), firstRoot.serializeFieldElement()))

    merkleTree.freeLazyMerkleTree() //looks like doesn't free under layered db because LOCK file can't be deleted
    try {
      merkleTree.root()
      assert(false, "Operation of freed trees shall throw exception")
    }
    catch {
      case ex: IllegalArgumentException => //got expected exception
      case e: Exception => assert(false, s"Got unexpected exception: ${e}")
    }

    println(new File(merkleTreeStatePath).exists())
    println(new File(dbPath).exists())
    println(new File(cachePath).exists())

    //Failed currently big lazy merkle tree is not save his state into the file. We need to handle it, btw, otherwise in case of unexpected shutdown we could get inconsistent tree state
    //val loadedMerkleTree = BigLazyMerkleTree.init(merkleTreeHeight, merkleTreeStatePath, dbPath, cachePath)
    //assert(util.Arrays.equals(loadedMerkleTree.root().serializeFieldElement(), firstRoot.serializeFieldElement()))
  }
}
