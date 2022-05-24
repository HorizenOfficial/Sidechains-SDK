package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap, List => JList}

import com.google.inject.name.Named
import com.google.inject.Provides
import com.horizen.api.http.ApplicationApiGroup
import com.horizen.box.BoxSerializer
import com.horizen.helper.{NodeViewHelper, NodeViewHelperImpl, SecretSubmitHelper, SecretSubmitHelperImpl, TransactionSubmitHelper, TransactionSubmitHelperImpl}
import com.horizen.secret.SecretSerializer
import com.horizen.state.ApplicationState
import com.horizen.storage.Storage
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.Pair
import com.horizen.wallet.ApplicationWallet

abstract class SidechainAppModule extends com.google.inject.AbstractModule {

  var app: SidechainApp = null

  override def configure(): Unit = {

    bind(classOf[NodeViewHelper])
      .to(classOf[NodeViewHelperImpl])

    bind(classOf[TransactionSubmitHelper])
      .to(classOf[TransactionSubmitHelperImpl])

    bind(classOf[SecretSubmitHelper])
      .to(classOf[SecretSubmitHelperImpl])

    configureApp()
  }

  def configureApp(): Unit

  @Provides
  def get(
          @Named("SidechainSettings") sidechainSettings: SidechainSettings,
          @Named("CustomBoxSerializers") customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]],
          @Named("CustomSecretSerializers")  customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
          @Named("CustomTransactionSerializers")  customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]],
          @Named("ApplicationWallet")  applicationWallet: ApplicationWallet,
          @Named("ApplicationState")  applicationState: ApplicationState,
          @Named("SecretStorage")  secretStorage: Storage,
          @Named("WalletBoxStorage")  walletBoxStorage: Storage,
          @Named("WalletTransactionStorage")  walletTransactionStorage: Storage,
          @Named("StateStorage")  stateStorage: Storage,
          @Named("StateForgerBoxStorage") forgerBoxStorage: Storage,
          @Named("StateUtxoMerkleTreeStorage") utxoMerkleTreeStorage: Storage,
          @Named("HistoryStorage")  historyStorage: Storage,
          @Named("WalletForgingBoxesInfoStorage")  walletForgingBoxesInfoStorage: Storage,
          @Named("WalletCswDataStorage") walletCswDataStorage: Storage,
          @Named("ConsensusStorage")  consensusStorage: Storage,
          @Named("CustomApiGroups")  customApiGroups: JList[ApplicationApiGroup],
          @Named("RejectedApiPaths")  rejectedApiPaths : JList[Pair[String, String]]
         ): SidechainApp = {
    synchronized {
      if (app == null) {
        app = new SidechainApp(
          sidechainSettings,
          customBoxSerializers,
          customSecretSerializers,
          customTransactionSerializers,
          applicationWallet,
          applicationState,
          secretStorage,
          walletBoxStorage,
          walletTransactionStorage,
          stateStorage,
          forgerBoxStorage,
          utxoMerkleTreeStorage,
          historyStorage,
          walletForgingBoxesInfoStorage,
          walletCswDataStorage,
          consensusStorage,
          customApiGroups,
          rejectedApiPaths
        )
      }
    }
    app
  }

}
