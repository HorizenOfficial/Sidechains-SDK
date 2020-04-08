package com.horizen.validation

import com.horizen.SidechainHistory
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader, SidechainBlock}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.fixtures.{MainchainBlockReferenceFixture, SidechainBlockInfoFixture}
import com.horizen.params.{MainNetParams, NetworkParams}
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.util.{ModifierId, bytesToId}
import org.junit.Assert.{assertEquals, fail => jFail}
import java.util.{Optional => JOptional}

import com.horizen.utils.WithdrawalEpochInfo
import scorex.core.consensus.ModifierSemanticValidity

import scala.util.{Failure, Success}

class MainchainBlockReferenceValidatorTest
  extends JUnitSuite
  with MockitoSugar
  with MainchainBlockReferenceFixture
  with SidechainBlockInfoFixture {

  val genesisParentBlockId: ModifierId = bytesToId(new Array[Byte](32))
  val genesisBlockId: ModifierId = bytesToId(new Array[Byte](32))
  val params: NetworkParams = MainNetParams(sidechainGenesisBlockId = genesisBlockId)
  val validator: MainchainBlockReferenceValidator = new MainchainBlockReferenceValidator(params)


  @Test
  def validateGenesisBlock(): Unit = {
    val ref1: MainchainBlockReference = generateMainchainBlockReference()
    val ref2: MainchainBlockReference = generateMainchainBlockReference()
    val history: SidechainHistory = mock[SidechainHistory]


    // Test 1: Sidechain block with 1 MainchainHeader and 1 MainchainReferenceData
    val validBlock: SidechainBlock = mockBlock(genesisBlockId, genesisParentBlockId, Seq(ref1.header), Seq(ref1.data))
    validator.validate(validBlock, history) match {
      case Success(_) =>
      case Failure(e) => jFail("Genesis block with 1 MainchainHeader and 1 MainchainReferenceData for it expected to be valid.")
    }


    // Test 2: Sidechain block with 2 MainchainHeader and 1 MainchainReferenceData
    var invalidBlock: SidechainBlock = mockBlock(genesisBlockId, genesisParentBlockId, Seq(ref1.header, ref2.header), Seq(ref1.data))
    validator.validate(invalidBlock, history) match {
      case Success(_) => jFail("Genesis block with multiple MainchainHeaders expected to be invalid.")
      case Failure(e) =>
    }


    // Test 3: Sidechain block with 1 MainchainHeader and 2 MainchainReferenceData
    invalidBlock = mockBlock(genesisBlockId, genesisParentBlockId, Seq(ref1.header), Seq(ref1.data, ref2.data))
    validator.validate(invalidBlock, history) match {
      case Success(_) => jFail("Genesis block with multiple MainchainReferenceData expected to be invalid.")
      case Failure(e) =>
    }


    // Test 4: Sidechain block with 1 MainchainHeader and 1 MainchainReferenceData, that headerHash is different to Header hash
    invalidBlock = mockBlock(genesisBlockId, genesisParentBlockId, Seq(ref1.header), Seq(ref2.data))
    validator.validate(invalidBlock, history) match {
      case Success(_) => jFail("Genesis block with MainchainHeader and not corresponding MainchainReferenceData expected to be invalid.")
      case Failure(e) =>
    }


    // Test 5: Sidechain block with no MainchainHeader and no MainchainReferenceData
    invalidBlock = mockBlock(genesisBlockId, genesisParentBlockId, Seq(), Seq())
    validator.validate(invalidBlock, history) match {
      case Success(_) => jFail("Genesis block with no MainchainHeaders and MainchainReferenceData expected to be invalid.")
      case Failure(e) =>
    }
  }

  @Test
  def validateBlock(): Unit = {
    // SC block:    G       B1      B2        B3        B4        B5
    // McHeaders:   1       2,3     4,5,6     -         7         8
    // McData:      1       -       2         3,4       5         6,7,8
    val ref1: MainchainBlockReference = generateMainchainBlockReference()
    val ref2: MainchainBlockReference = generateMainchainBlockReference()
    val ref3: MainchainBlockReference = generateMainchainBlockReference()
    val ref4: MainchainBlockReference = generateMainchainBlockReference()
    val ref5: MainchainBlockReference = generateMainchainBlockReference()
    val ref6: MainchainBlockReference = generateMainchainBlockReference()
    val ref7: MainchainBlockReference = generateMainchainBlockReference()
    val ref8: MainchainBlockReference = generateMainchainBlockReference()
    val ref9: MainchainBlockReference = generateMainchainBlockReference()
    val ref10: MainchainBlockReference = generateMainchainBlockReference()

    val genesisBlock: SidechainBlock = mockBlock(genesisBlockId, genesisParentBlockId, Seq(ref1.header), Seq(ref1.data))
    val block1: SidechainBlock = mockBlock(getRandomModifier(), genesisBlock.id, Seq(ref2.header, ref3.header), Seq())
    val block2: SidechainBlock = mockBlock(getRandomModifier(), block1.id, Seq(ref4.header, ref5.header, ref6.header), Seq(ref2.data))
    val block3: SidechainBlock = mockBlock(getRandomModifier(), block2.id, Seq(), Seq(ref3.data, ref4.data))
    val block4: SidechainBlock = mockBlock(getRandomModifier(), block3.id, Seq(ref7.header), Seq(ref5.data))
    val block5: SidechainBlock = mockBlock(getRandomModifier(), block4.id, Seq(ref8.header), Seq(ref6.data, ref7.data, ref8.data))

    val blocks: Seq[SidechainBlock] = Seq(genesisBlock, block1, block2, block3, block4, block5)
    val blocksInfo: Seq[(ModifierId, SidechainBlockInfo)] = blocks.map(b => (b.id, getBlockInfo(b)))

    val history: SidechainHistory = mock[SidechainHistory]
    Mockito.when(history.blockInfoById(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val id: ModifierId = answer.getArgument(0)
      blocksInfo.find(info => info._1 == id).get._2
    })

    Mockito.when(history.getBlockById(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val id: ModifierId = answer.getArgument(0)
      JOptional.ofNullable(blocks.find(block => block.id == id).orNull)
    })


    // Test 1: validate block with MainchainHeaders only
    validator.validate(block1, history) match {
      case Success(_) =>
      case Failure(e) => jFail("SidechainBlock with 2 MainchainHeaders and no MainchainReferenceData expected to be valid.")
    }


    // Test 2: validate block with multiple MainchainHeaders and 1 MainchainReferenceData, which MainchainHeader was specified in previous block
    validator.validate(block2, history) match {
      case Success(_) =>
      case Failure(e) => jFail("SidechainBlock with 3 MainchainHeaders and MainchainReferenceData for previous block MainchainHeader expected to be valid.")
    }


    // Test 3: validate block 2 MainchainReferenceData, which MainchainHeaders were specified in previous blocks (different)
    validator.validate(block3, history) match {
      case Success(_) =>
      case Failure(e) => jFail("SidechainBlock with 2 MainchainReferenceData, which MainchainHeaders were specified in previous blocks, expected to be valid.")
    }


    // Test 4: validate block with 1 MainchainHeader and 1 MainchainReferenceData, which MainchainHeader was specified in block in the past (not previous).
    validator.validate(block4, history) match {
      case Success(_) =>
      case Failure(e) => jFail("SidechainBlock with1 MainchainHeader and 1 MainchainReferenceData, which MainchainHeader was specified in block in the past, expected to be valid.")
    }


    // Test 5: validate block with 1 MainchainHeader and 3 MainchainReferenceData, which MainchainHeader was specified in current block and blocks in the past.
    validator.validate(block5, history) match {
      case Success(_) =>
      case Failure(e) => jFail("SidechainBlock with 1 MainchainHeader and 3 MainchainReferenceData, which MainchainHeader was specified in current block and blocks in the past, expected to be valid.")
    }


    // Test 6: validate block, that leads to more MainchainReferenceData than MainchainHeaders in the chain.
    var invalidBlock: SidechainBlock = mockBlock(getRandomModifier(), block5.id, Seq(), Seq(ref9.data))
    validator.validate(invalidBlock, history) match {
      case Success(_) => jFail("SidechainBlock, that leads to more MainchainReferenceData than MainchainHeaders in the chain, expected to be Invalid.")
      case Failure(_) =>
    }
    // Same case, but with looking into history as well
    invalidBlock = mockBlock(getRandomModifier(), block1.id, Seq(), Seq(ref2.data, ref3.data, ref4.data))
    validator.validate(invalidBlock, history) match {
      case Success(_) => jFail("SidechainBlock, that leads to more MainchainReferenceData than MainchainHeaders in the chain, expected to be Invalid.")
      case Failure(_) =>
    }


    // Test 7: validate block, that contains inconsistent MainchainReferenceData to MainchainHeader in it.
    invalidBlock = mockBlock(getRandomModifier(), block5.id, Seq(ref9.header), Seq(ref10.data))
    validator.validate(invalidBlock, history) match {
      case Success(_) => jFail("SidechainBlock, that contains inconsistent MainchainReferenceData to MainchainHeader in it, expected to be Invalid.")
      case Failure(_) =>
    }
    // Same case, but with looking into history as well
    invalidBlock = mockBlock(getRandomModifier(), block4.id, Seq(ref8.header), Seq(ref6.data, ref8.data, ref8.data))
    validator.validate(invalidBlock, history) match {
      case Success(_) => jFail("SidechainBlock, that leads to more MainchainReferenceData than MainchainHeaders in the chain, expected to be Invalid.")
      case Failure(_) =>
    }
  }

  private def mockBlock(id: ModifierId, parentId: ModifierId, headers: Seq[MainchainHeader], refData: Seq[MainchainBlockReferenceData]): SidechainBlock = {
    val block: SidechainBlock = mock[SidechainBlock]
    Mockito.when(block.id).thenReturn(id)
    Mockito.when(block.parentId).thenReturn(parentId)
    Mockito.when(block.mainchainHeaders).thenReturn(headers)
    Mockito.when(block.mainchainBlockReferencesData).thenReturn(refData)
    block
  }

  private def getBlockInfo(block: SidechainBlock): SidechainBlockInfo = {
    // Specify only the parts used in Validator
    SidechainBlockInfo(
      0,
      0,
      block.parentId,
      0L,
      ModifierSemanticValidity.Unknown,
      SidechainBlockInfo.mainchainHeaderHashesFromBlock(block),
      SidechainBlockInfo.mainchainReferenceDataHeaderHashesFromBlock(block),
      WithdrawalEpochInfo(0, 0)
    )
  }
}
