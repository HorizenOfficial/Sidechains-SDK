package com.horizen.account.block

import com.horizen.SidechainTypes
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.block._
import com.horizen.utils.ListSerializer
import sparkz.util.serialization.{Reader, Writer}
import sparkz.core.serialization.SparkzSerializer

import scala.collection.JavaConverters._

class AccountBlockSerializer(companion: SidechainAccountTransactionsCompanion) extends SparkzSerializer[AccountBlock] with SidechainTypes {
  require(companion != null, "SidechainAccountTransactionsCompanion must be NOT NULL.")

  private val mcBlocksDataSerializer: ListSerializer[MainchainBlockReferenceData] = new ListSerializer[MainchainBlockReferenceData](
    MainchainBlockReferenceDataSerializer
  )

  private val sidechainTransactionsSerializer: ListSerializer[SidechainTypes#SCAT] =
    new ListSerializer[SidechainTypes#SCAT](
      companion
    )

  private val mainchainHeadersSerializer: ListSerializer[MainchainHeader] = new ListSerializer[MainchainHeader](MainchainHeaderSerializer)

  private val ommersSerializer: ListSerializer[Ommer[AccountBlockHeader]] = new ListSerializer[Ommer[AccountBlockHeader]](AccountOmmerSerializer)

  override def serialize(obj: AccountBlock, w: Writer): Unit = {
    AccountBlockHeaderSerializer.serialize(obj.header, w)
    sidechainTransactionsSerializer.serialize(obj.sidechainTransactions.asJava, w)
    mcBlocksDataSerializer.serialize(obj.mainchainBlockReferencesData.asJava, w)
    mainchainHeadersSerializer.serialize(obj.mainchainHeaders.asJava, w)
    ommersSerializer.serialize(obj.ommers.asJava, w)
  }

  override def parse(r: Reader): AccountBlock = {

    require(r.remaining <= SidechainBlock.MAX_BLOCK_SIZE)

    val SidechainAccountBlockHeader: AccountBlockHeader = AccountBlockHeaderSerializer.parse(r)
    val sidechainTransactions = sidechainTransactionsSerializer.parse(r).asScala
    val mainchainBlockReferencesData = mcBlocksDataSerializer.parse(r).asScala
    val mainchainHeaders = mainchainHeadersSerializer.parse(r).asScala
    val ommers = ommersSerializer.parse(r).asScala

    new AccountBlock(
      SidechainAccountBlockHeader,
      sidechainTransactions,
      mainchainBlockReferencesData,
      mainchainHeaders,
      ommers,
      companion
    )
  }
}
