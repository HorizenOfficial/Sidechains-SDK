package io.horizen.examples;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import io.horizen.utxo.SidechainAppModule;
import io.horizen.SidechainAppStopper;
import io.horizen.SidechainSettings;
import io.horizen.utxo.api.http.SidechainApplicationApiGroup;
import io.horizen.fork.ForkConfigurator;
import io.horizen.proposition.Proposition;
import io.horizen.secret.Secret;
import io.horizen.secret.SecretSerializer;
import io.horizen.settings.SettingsReader;
import io.horizen.storage.Storage;
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import io.horizen.utxo.transaction.BoxTransaction;
import io.horizen.transaction.TransactionSerializer;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.BoxSerializer;
import io.horizen.utxo.state.ApplicationState;
import io.horizen.utxo.wallet.ApplicationWallet;
import io.horizen.utils.Pair;

public class SimpleAppModule extends SidechainAppModule
{
    private final SettingsReader settingsReader;

    // It's integer parameter that defines Mainchain Block Reference delay.
    // 1 or 2 should be enough to avoid SC block reverting in the most cases.
    // WARNING. It must be constant and should not be changed inside Sidechain network
    private final int mcBlockRefDelay;
    private final int maxHistRewLen;


    public SimpleAppModule(String userSettingsFileName, int mcBlockDelayReference, boolean allForksEnabled, int maxHistRewLen) {
        // note: allForksEnabled param currently not used
        this.settingsReader = new SettingsReader(userSettingsFileName, Optional.empty());
        this.mcBlockRefDelay = mcBlockDelayReference;
        this.maxHistRewLen = maxHistRewLen;
    }

    @Override
    public void configureApp() {

        SidechainSettings sidechainSettings = this.settingsReader.getSidechainSettings();
        int maxHistoryRewritingLength = 200;

        HashMap<Byte, BoxSerializer<Box<Proposition>>> customBoxSerializers = new HashMap<>();
        HashMap<Byte, SecretSerializer<Secret>> customSecretSerializers = new HashMap<>();
        HashMap<Byte, TransactionSerializer<BoxTransaction<Proposition, Box<Proposition>>>> customTransactionSerializers = new HashMap<>();

        String dataDirAbsolutePath = sidechainSettings.sparkzSettings().dataDir().getAbsolutePath();

        //Initialize the App Fork Configurator
        AppForkConfigurator forkConfigurator = new AppForkConfigurator();

        // two distinct storages are used in application state and wallet in order to test a version
        // misalignment during startup and the recover logic
        File appWalletStorage1 = new File(dataDirAbsolutePath + "/appWallet1");
        File appWalletStorage2 = new File(dataDirAbsolutePath + "/appWallet2");
        DefaultApplicationWallet defaultApplicationWallet = new DefaultApplicationWallet(appWalletStorage1, appWalletStorage2, maxHistoryRewritingLength);

        File appStateStorage1 = new File(dataDirAbsolutePath + "/appState1");
        File appStateStorage2 = new File(dataDirAbsolutePath + "/appState2");
        DefaultApplicationState defaultApplicationState = new DefaultApplicationState(appStateStorage1, appStateStorage2, maxHistoryRewritingLength);

        File secretStore = new File(dataDirAbsolutePath + "/secret");
        File walletBoxStore = new File(dataDirAbsolutePath + "/wallet");
        File walletTransactionStore = new File(dataDirAbsolutePath + "/walletTransaction");
        File walletForgingBoxesInfoStorage = new File(dataDirAbsolutePath + "/walletForgingStake");
        File walletCswDataStorage = new File(dataDirAbsolutePath + "/walletCswDataStorage");
        File stateStore = new File(dataDirAbsolutePath + "/state");
        File stateForgerBoxStore = new File(dataDirAbsolutePath + "/stateForgerBox");
        File stateUtxoMerkleTreeStore = new File(dataDirAbsolutePath + "/stateUtxoMerkleTree");
        File historyStore = new File(dataDirAbsolutePath + "/history");
        File consensusStore = new File(dataDirAbsolutePath + "/consensusData");
        File backupStore = new File(dataDirAbsolutePath + "/backupStorage");

        String appVersion = "";

        // Here I can add my custom rest api and/or override existing one
        List<SidechainApplicationApiGroup> customApiGroups = new ArrayList<>();

        // Here I can reject some of existing API routes
        // Each pair consists of "group name" -> "route name"
        // For example new Pair("wallet, "allBoxes");
        List<Pair<String, String>> rejectedApiPaths = new ArrayList<>();

        // use a custom object which implements the stopAll() method
        SidechainAppStopper applicationStopper = new SimpleAppStopper(
                defaultApplicationState, defaultApplicationWallet);

        bind(SidechainSettings.class)
                .annotatedWith(Names.named("SidechainSettings"))
                .toInstance(sidechainSettings);

        bind(new TypeLiteral<HashMap<Byte, BoxSerializer<Box<Proposition>>>>() {})
                .annotatedWith(Names.named("CustomBoxSerializers"))
                .toInstance(customBoxSerializers);
        bind(new TypeLiteral<HashMap<Byte, SecretSerializer<Secret>>>() {})
                .annotatedWith(Names.named("CustomSecretSerializers"))
                .toInstance(customSecretSerializers);
        bind(new TypeLiteral<HashMap<Byte, TransactionSerializer<BoxTransaction<Proposition, Box<Proposition>>>>>() {})
                .annotatedWith(Names.named("CustomTransactionSerializers"))
                .toInstance(customTransactionSerializers);

        bind(ApplicationWallet.class)
                .annotatedWith(Names.named("ApplicationWallet"))
                .toInstance(defaultApplicationWallet);

        bind(ApplicationState.class)
                .annotatedWith(Names.named("ApplicationState"))
                .toInstance(defaultApplicationState);


        bind(Storage.class)
                .annotatedWith(Names.named("SecretStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(secretStore, 5));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletBoxStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(walletBoxStore, maxHistoryRewritingLength));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletTransactionStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(walletTransactionStore, maxHistoryRewritingLength));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletForgingBoxesInfoStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(walletForgingBoxesInfoStorage, maxHistoryRewritingLength));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletCswDataStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(walletCswDataStorage, maxHistoryRewritingLength));
        bind(Storage.class)
                .annotatedWith(Names.named("StateStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(stateStore, maxHistoryRewritingLength));
        bind(Storage.class)
                .annotatedWith(Names.named("StateForgerBoxStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(stateForgerBoxStore, maxHistoryRewritingLength));
        bind(Storage.class)
                .annotatedWith(Names.named("StateUtxoMerkleTreeStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(stateUtxoMerkleTreeStore, maxHistoryRewritingLength));
        bind(Storage.class)
                .annotatedWith(Names.named("HistoryStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(historyStore, 5));
        bind(Storage.class)
                .annotatedWith(Names.named("ConsensusStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(consensusStore, 5));
        bind(Storage.class)
                .annotatedWith(Names.named("BackupStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(backupStore, maxHistoryRewritingLength));

        bind(new TypeLiteral<List<SidechainApplicationApiGroup>> () {})
                .annotatedWith(Names.named("CustomApiGroups"))
                .toInstance(customApiGroups);

        bind(new TypeLiteral<List<Pair<String, String>>> () {})
                .annotatedWith(Names.named("RejectedApiPaths"))
                .toInstance(rejectedApiPaths);

        bind(SidechainAppStopper.class)
                .annotatedWith(Names.named("ApplicationStopper"))
                .toInstance(applicationStopper);

        bind(ForkConfigurator.class)
                .annotatedWith(Names.named("ForkConfiguration"))
                .toInstance(forkConfigurator);

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
