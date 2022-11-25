package com.horizen.account

import com.google.inject.Provides
import com.google.inject.name.Named
import com.horizen.account.state.MessageProcessor
import com.horizen.api.http.ApplicationApiGroup
import com.horizen.fork.ForkConfigurator
import com.horizen.secret.SecretSerializer
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.Pair
import com.horizen.{ChainInfo, SidechainAppStopper, SidechainSettings, SidechainTypes}

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap, List => JList}

abstract class AccountAppModule extends com.google.inject.AbstractModule {

  var app: AccountSidechainApp = null

  override def configure(): Unit = {
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
          @Named("CustomMessageProcessors") customMessageProcessors: JList[MessageProcessor],
          @Named("ApplicationStopper") applicationStopper : SidechainAppStopper,
          @Named("ForkConfiguration") forkConfigurator : ForkConfigurator
         ): AccountSidechainApp = {
    synchronized {
      if (app == null) {
        app = new AccountSidechainApp(
          sidechainSettings,
          customSecretSerializers,
          customAccountTransactionSerializers,
          customApiGroups,
          rejectedApiPaths,
          customMessageProcessors,
          applicationStopper,
          forkConfigurator,
          chainInfo
        )
      }
    }
    app
  }

}