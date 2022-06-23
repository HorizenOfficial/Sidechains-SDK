package com.horizen.account

import com.google.inject.Provides
import com.google.inject.name.Named
import com.horizen.account.state.MessageProcessor
import com.horizen.api.http.ApplicationApiGroup
import com.horizen.box.BoxSerializer
import com.horizen.helper._
import com.horizen.secret.SecretSerializer
import com.horizen.state.ApplicationState
import com.horizen.storage.Storage
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.Pair
import com.horizen.wallet.ApplicationWallet
import com.horizen.{SidechainApp, SidechainSettings, SidechainTypes, ChainInfo}

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap, List => JList}

abstract class AccountAppModule extends com.google.inject.AbstractModule {

  var app: AccountSidechainApp = null

  override def configure(): Unit = {

// if sb would like to have access to functionalities
//    bind(classOf[NodeViewHelper])
//      .to(classOf[NodeViewHelperImpl])

//    bind(classOf[TransactionSubmitHelper])
    //     .to(classOf[TransactionSubmitHelperImpl])

//    bind(classOf[SecretSubmitHelper])
//      .to(classOf[SecretSubmitHelperImpl])

    configureApp()
  }

  def configureApp(): Unit

  @Provides
  def get(
          @Named("SidechainSettings") sidechainSettings: SidechainSettings,
          @Named("CustomSecretSerializers")  customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
          @Named("CustomAccountTransactionSerializers")  customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]],
          @Named("CustomApiGroups")  customApiGroups: JList[ApplicationApiGroup],
          @Named("RejectedApiPaths")  rejectedApiPaths : JList[Pair[String, String]],
          @Named("ChainInfo") chainInfo : ChainInfo,
          @Named("CustomMessageProcessors") customMessageProcessors: JList[MessageProcessor]
         ): AccountSidechainApp = {
    synchronized {
      if (app == null) {
        app = new AccountSidechainApp(
          sidechainSettings,
          customSecretSerializers,
          customAccountTransactionSerializers,
          customApiGroups,
          rejectedApiPaths,
          chainInfo,
          customMessageProcessors
        )
      }
    }
    app
  }

}
