package com.horizen.examples;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.horizen.SidechainAppModule;
import com.horizen.SidechainAppStopper;
import com.horizen.SidechainSettings;
import com.horizen.api.http.ApplicationApiGroup;
import com.horizen.box.*;
import com.horizen.fork.ForkConfigurator;
import com.horizen.proposition.Proposition;
import com.horizen.sc2sc.Sc2ScConfigurator;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretSerializer;
import com.horizen.settings.SettingsReader;
import com.horizen.storage.Storage;
import com.horizen.state.*;
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import com.horizen.transaction.BoxTransaction;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.wallet.*;
import com.horizen.utils.Pair;
import org.checkerframework.checker.units.qual.A;

public class SimpleAppModule extends SidechainAppModule
{
    private final SettingsReader settingsReader;

    public SimpleAppModule(String userSettingsFileName) {
        this.settingsReader = new SettingsReader(userSettingsFileName, Optional.empty());
    }

    @Override
    public void configureApp() {

        SidechainSettings sidechainSettings = this.settingsReader.getSidechainSettings();

        HashMap<Byte, BoxSerializer<Box<Proposition>>> customBoxSerializers = new HashMap<>();
        HashMap<Byte, SecretSerializer<Secret>> customSecretSerializers = new HashMap<>();
        HashMap<Byte, TransactionSerializer<BoxTransaction<Proposition, Box<Proposition>>>> customTransactionSerializers = new HashMap<>();

        String dataDirAbsolutePath = sidechainSettings.sparkzSettings().dataDir().getAbsolutePath();

        // two distinct storages are used in application state and wallet in order to test a version
        // misalignment during startup and the recover logic
        File appWalletStorage1 = new File(dataDirAbsolutePath + "/appWallet1");
        File appWalletStorage2 = new File(dataDirAbsolutePath + "/appWallet2");
        DefaultApplicationWallet defaultApplicationWallet = new DefaultApplicationWallet(appWalletStorage1, appWalletStorage2);

        File appStateStorage1 = new File(dataDirAbsolutePath + "/appState1");
        File appStateStorage2 = new File(dataDirAbsolutePath + "/appState2");
        DefaultApplicationState defaultApplicationState = new DefaultApplicationState(appStateStorage1, appStateStorage2);

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

        AppForkConfigurator forkConfigurator = new AppForkConfigurator();

        // It's integer parameter that defines slot duration. The minimum valid value is 10, the maximum is 300.
        int consensusSecondsInSlot = 120;

        // Here I can add my custom rest api and/or override existing one
        List<ApplicationApiGroup> customApiGroups = new ArrayList<>();

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
                .toInstance(new VersionedLevelDbStorageAdapter(secretStore));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletBoxStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(walletBoxStore));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletTransactionStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(walletTransactionStore));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletForgingBoxesInfoStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(walletForgingBoxesInfoStorage));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletCswDataStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(walletCswDataStorage));
        bind(Storage.class)
                .annotatedWith(Names.named("StateStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(stateStore));
        bind(Storage.class)
                .annotatedWith(Names.named("StateForgerBoxStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(stateForgerBoxStore));
        bind(Storage.class)
                .annotatedWith(Names.named("StateUtxoMerkleTreeStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(stateUtxoMerkleTreeStore));
        bind(Storage.class)
                .annotatedWith(Names.named("HistoryStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(historyStore));
        bind(Storage.class)
                .annotatedWith(Names.named("ConsensusStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(consensusStore));
        bind(Storage.class)
                .annotatedWith(Names.named("BackupStorage"))
                .toInstance(new VersionedLevelDbStorageAdapter(backupStore));

        bind(new TypeLiteral<List<ApplicationApiGroup>> () {})
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
        bind(Integer.class)
                .annotatedWith(Names.named("ConsensusSecondsInSlot"))
                .toInstance(consensusSecondsInSlot);
        bind(Sc2ScConfigurator.class)
                .annotatedWith(Names.named("Sc2ScConfiguration"))
                .toInstance(new Sc2ScConfigurator(true, true) );

    }
}
