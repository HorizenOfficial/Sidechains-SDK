import java.io.File
import java.util
import java.util.Random

import com.horizen.librustsidechains.FieldElement
import com.horizen.merkletreenative.BigLazyMerkleTree
import org.junit.Test

import scala.collection.JavaConverters._

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
    val merkleTreeStatePath = createNewTempDirPath().getAbsolutePath// + File.separator + "state"
    //println(new File(merkleTreeStatePath).mkdirs())
    //println(merkleTreeStatePath)
    val dbPath = createNewTempDirPath().getAbsolutePath
    //println(new File(dbPath).mkdirs())

    val cachePath = createNewTempDirPath().getAbsolutePath
    //println(new File(cachePath).mkdirs())

    println(merkleTreeStatePath)
    println(new File(merkleTreeStatePath).exists())

    println(dbPath)
    println(new File(dbPath).exists())

    println(cachePath)
    println(new File(cachePath).exists())

    //all paths shall not exist in case if new tree is created
    val merkleTree = BigLazyMerkleTree.init(merkleTreeHeight, merkleTreeStatePath, dbPath, cachePath)
    println("tree had been created")
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

    println("Before free lazy tree")

    Thread.sleep(5000)

    merkleTree.freeLazyMerkleTree() //looks like doesn't free under layered db because LOCK file can't be deleted
    try {
      merkleTree.root()
      assert(false, "Operation on trees shall throw exception")
    }
    catch {
      case ex: IllegalStateException => //got expected exception
      case e: Exception => assert(false, s"Got unexpected exception: ${e}")
    }


    assert(new File(merkleTreeStatePath).exists())
    assert(new File(dbPath).exists())
    assert(new File(cachePath).exists())

    //Failed currently big lazy merkle tree is not save his state into the file. We need to handle it, btw, otherwise in case of unexpected shutdown we could get inconsistent tree state
    val loadedMerkleTree = BigLazyMerkleTree.init(merkleTreeHeight, merkleTreeStatePath, dbPath, cachePath)
    assert(util.Arrays.equals(loadedMerkleTree.root().serializeFieldElement(), firstRoot.serializeFieldElement()))

    merkleTree.freeAndDestroyLazyMerkleTree()
    //assert(!new File(merkleTreeStatePath).exists()) //file is not deleted
    //assert(!new File(dbPath).exists()) //Directory is not deleted LOCK file is stay
    //assert(!new File(cachePath).exists()) //Directory is not deleted LOCK file is stay
    /*try {
      val loadedMerkleTree2 = BigLazyMerkleTree.init(merkleTreeHeight, merkleTreeStatePath, dbPath, cachePath)
    }
    catch {
      case e: Exception => println(e)
    }*/ //java exception shall be thrown, not EXCEPTION_UNCAUGHT_CXX_EXCEPTION
  }
}
