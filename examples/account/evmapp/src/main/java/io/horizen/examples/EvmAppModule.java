package io.horizen.examples;

import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import io.horizen.ChainInfo;
import io.horizen.SidechainAppStopper;
import io.horizen.SidechainSettings;
import io.horizen.account.AccountAppModule;
import io.horizen.account.state.EvmMessageProcessor;
import io.horizen.account.state.MessageProcessor;
import io.horizen.account.transaction.AccountTransaction;
import io.horizen.account.api.http.AccountApplicationApiGroup;
import io.horizen.fork.ForkConfigurator;
import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;
import io.horizen.secret.Secret;
import io.horizen.secret.SecretSerializer;
import io.horizen.settings.SettingsReader;
import io.horizen.transaction.TransactionSerializer;
import io.horizen.utils.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class EvmAppModule extends AccountAppModule {
    private final SettingsReader settingsReader;

    // It's integer parameter that defines Mainchain Block Reference delay.
    // 1 or 2 should be enough to avoid SC block reverting in the most cases.
    // WARNING. It must be constant and should not be changed inside Sidechain network
    private final int mcBlockRefDelay;
    private final boolean allForksEnabled;
    private final int maxHistRewLen;


    public EvmAppModule(String userSettingsFileName, int mcBlockDelayReference, boolean allForksEnabled, int maxHistRewLen) {
        this.settingsReader = new SettingsReader(userSettingsFileName, Optional.empty());
        this.mcBlockRefDelay = mcBlockDelayReference;
        this.allForksEnabled = allForksEnabled;
        this.maxHistRewLen = maxHistRewLen;
    }

    @Override
    public void configureApp() {

        long regTestId = 1000000001;
        long testNetId = 1000000002;
        long mainNetId = 1000000003;

        SidechainSettings sidechainSettings = this.settingsReader.getSidechainSettings();

        HashMap<Byte, SecretSerializer<Secret>> customSecretSerializers = new HashMap<>();
        HashMap<Byte, TransactionSerializer<AccountTransaction<Proposition, Proof<Proposition>>>>
                customAccountTransactionSerializers = new HashMap<>();

        ForkConfigurator forkConfigurator = allForksEnabled ? new AppForkConfiguratorAllEnabledFromEpoch2() : new AppForkConfigurator();

        // Here I can add my custom rest api and/or override existing one
        List<AccountApplicationApiGroup> customApiGroups = new ArrayList<>();

        // Here I can reject some of existing API routes
        // Each pair consists of "group name" -> "route name"
        // For example new Pair("wallet, "allBoxes");
        List<Pair<String, String>> rejectedApiPaths = new ArrayList<>();

        ChainInfo chainInfo = new ChainInfo(regTestId, testNetId, mainNetId);

        // Here I can add my custom logic to manage EthereumTransaction content.
        List<MessageProcessor> customMessageProcessors = new ArrayList<>();
        customMessageProcessors.add(new EvmMessageProcessor());

        String appVersion = "";

        // use a custom object which implements the stopAll() method
        SidechainAppStopper applicationStopper = new EvmAppStopper();

        bind(SidechainSettings.class)
                .annotatedWith(Names.named("SidechainSettings"))
                .toInstance(sidechainSettings);

        bind(new TypeLiteral<HashMap<Byte, SecretSerializer<Secret>>>() {})
                .annotatedWith(Names.named("CustomSecretSerializers"))
                .toInstance(customSecretSerializers);

        bind(new TypeLiteral<HashMap<Byte, TransactionSerializer<AccountTransaction<Proposition, Proof<Proposition>>>>>() {})
                .annotatedWith(Names.named("CustomAccountTransactionSerializers"))
                .toInstance(customAccountTransactionSerializers);

        bind(new TypeLiteral<List<AccountApplicationApiGroup>>() {})
                .annotatedWith(Names.named("CustomApiGroups"))
                .toInstance(customApiGroups);

        bind(new TypeLiteral<List<Pair<String, String>>>() {})
                .annotatedWith(Names.named("RejectedApiPaths"))
                .toInstance(rejectedApiPaths);

        bind(SidechainAppStopper.class)
                .annotatedWith(Names.named("ApplicationStopper"))
                .toInstance(applicationStopper);

        bind(ForkConfigurator.class)
                .annotatedWith(Names.named("ForkConfiguration"))
                .toInstance(forkConfigurator);

        bind(ChainInfo.class)
                .annotatedWith(Names.named("ChainInfo"))
                .toInstance(chainInfo);

        bind(new TypeLiteral<List<MessageProcessor>>() {})
                .annotatedWith(Names.named("CustomMessageProcessors"))
                .toInstance(customMessageProcessors);

        bind(String.class)
                .annotatedWith(Names.named("AppVersion"))
                .toInstance(appVersion);
        bind(Integer.class)
                .annotatedWith(Names.named("MainchainBlockReferenceDelay"))
                .toInstance(mcBlockRefDelay);
        bind(Integer.class)
                .annotatedWith(Names.named("MaxHistoryRewriteLength"))
                .toInstance(maxHistRewLen);
    }
}
