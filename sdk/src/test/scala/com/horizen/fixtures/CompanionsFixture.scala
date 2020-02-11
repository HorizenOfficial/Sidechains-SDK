package com.horizen.fixtures

import com.horizen.companion.{SidechainBoxesDataCompanion, SidechainProofsCompanion, SidechainTransactionsCompanion}
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes
import com.horizen.transaction.TransactionSerializer

trait CompanionsFixture
{
  def getDefaultTransactionsCompanion: SidechainTransactionsCompanion = {
    val sidechainBoxesDataCompanion = SidechainBoxesDataCompanion(new JHashMap())
    val sidechainProofsCompanion = SidechainProofsCompanion(new JHashMap())

    SidechainTransactionsCompanion(new JHashMap(), sidechainBoxesDataCompanion, sidechainProofsCompanion)
  }

  def getTransactionsCompanionWithCustomTransactions(customSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]): SidechainTransactionsCompanion = {
    val sidechainBoxesDataCompanion = SidechainBoxesDataCompanion(new JHashMap())
    val sidechainProofsCompanion = SidechainProofsCompanion(new JHashMap())

    SidechainTransactionsCompanion(customSerializers, sidechainBoxesDataCompanion, sidechainProofsCompanion)
  }
}

class CompanionsFixtureClass extends CompanionsFixture