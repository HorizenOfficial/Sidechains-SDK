package com.horizen.examples;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import com.horizen.SidechainAppModule;
import com.horizen.SidechainSettings;
import com.horizen.api.http.ApplicationApiGroup;
import com.horizen.box.*;
import com.horizen.proposition.Proposition;
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

        ApplicationWallet defaultApplicationWallet = new DefaultApplicationWallet();
        ApplicationState defaultApplicationState = new DefaultApplicationState();

        String dataDirAbsolutePath = sidechainSettings.scorexSettings().dataDir().getAbsolutePath();
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



        // Here I can add my custom rest api and/or override existing one
        List<ApplicationApiGroup> customApiGroups = new ArrayList<>();

        // Here I can reject some of existing API routes
        // Each pair consists of "group name" -> "route name"
        // For example new Pair("wallet, "allBoxes");
        List<Pair<String, String>> rejectedApiPaths = new ArrayList<>();



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

        bind(new TypeLiteral<List<ApplicationApiGroup>> () {})
                .annotatedWith(Names.named("CustomApiGroups"))
                .toInstance(customApiGroups);

        bind(new TypeLiteral<List<Pair<String, String>>> () {})
                .annotatedWith(Names.named("RejectedApiPaths"))
                .toInstance(rejectedApiPaths);
    }
}
