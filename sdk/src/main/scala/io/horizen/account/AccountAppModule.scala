package io.horizen.account

import com.google.inject.Provides
import com.google.inject.name.Named
import com.horizen.account.helper.{AccountTransactionSubmitHelper, AccountTransactionSubmitHelperImpl}
import com.horizen.account.helper.{AccountNodeViewHelper, AccountNodeViewHelperImpl}
import com.horizen.account.state.MessageProcessor
import com.horizen.api.http.ApplicationApiGroup
import com.horizen.fork.ForkConfigurator
import com.horizen.helper.{SecretSubmitHelper, SecretSubmitHelperImpl}
import com.horizen.secret.SecretSerializer
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.Pair
import com.horizen.{AbstractSidechainApp, ChainInfo, SidechainAppStopper, SidechainSettings, SidechainTypes}

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap, List => JList}

abstract class AccountAppModule extends com.google.inject.AbstractModule {

  var app: AccountSidechainApp = null

  override def configure(): Unit = {

    bind(classOf[AccountTransactionSubmitHelper])
      .to(classOf[AccountTransactionSubmitHelperImpl])


    bind(classOf[AbstractSidechainApp])
      .to(classOf[AccountSidechainApp])

    bind(classOf[AccountNodeViewHelper])
      .to(classOf[AccountNodeViewHelperImpl])

    bind(classOf[SecretSubmitHelper])
      .to(classOf[SecretSubmitHelperImpl])

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
          @Named("ForkConfiguration") forkConfigurator : ForkConfigurator,
          @Named("ConsensusSecondsInSlot") secondsInSlot: Int
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
          chainInfo,
          secondsInSlot
        )
      }
    }
    app
  }

}
