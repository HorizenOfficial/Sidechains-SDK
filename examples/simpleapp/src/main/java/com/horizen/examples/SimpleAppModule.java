package com.horizen.examples;

import java.io.File;
import java.util.HashMap;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import com.horizen.SidechainSettings;
import com.horizen.box.*;
import com.horizen.params.MainNetParams;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretSerializer;
import com.horizen.storage.Storage;
import com.horizen.state.*;
import com.horizen.transaction.BoxTransaction;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.wallet.*;

public class SimpleAppModule
    extends AbstractModule
{
    private SidechainSettings sidechainSettings;

    public SimpleAppModule(String userSettingsFileName) {
        this.sidechainSettings = SidechainSettings.read(userSettingsFileName);
    }

    @Override
    protected void configure() {

        HashMap<Byte, BoxSerializer<Box<Proposition>>> customBoxSerializers = new HashMap<>();
        HashMap<Byte, SecretSerializer<Secret>> customSecretSerializers = new HashMap<>();
        HashMap<Byte, TransactionSerializer<BoxTransaction<Proposition, Box<Proposition>>>> customTransactionSerializers = new HashMap<>();

        ApplicationWallet defaultApplicationWallet = new DefaultApplicationWallet();
        ApplicationState defaultApplicationState = new DefaultApplicationState();

        MainNetParams mainNetParams = new MainNetParams();

        File secretStore = new File(this.sidechainSettings.scorexSettings().dataDir().getAbsolutePath() + "/secret");
        File walletBoxStore = new File(this.sidechainSettings.scorexSettings().dataDir().getAbsolutePath() + "/wallet");
        File walletTransactionStore = new File(this.sidechainSettings.scorexSettings().dataDir().getAbsolutePath() + "/walletTransaction");
        File stateStore = new File(this.sidechainSettings.scorexSettings().dataDir().getAbsolutePath() + "/state");
        File historyStore = new File(this.sidechainSettings.scorexSettings().dataDir().getAbsolutePath() + "/history");

        bind(SidechainSettings.class)
                .annotatedWith(Names.named("SidechainSettings"))
                .toInstance(this.sidechainSettings);

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

        bind(MainNetParams.class)
                .annotatedWith(Names.named("MainNetParams"))
                .toInstance(mainNetParams);

        bind(Storage.class)
                .annotatedWith(Names.named("SecretStorage"))
                .toInstance(SidechainSettings.getStorage(secretStore));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletBoxStorage"))
                .toInstance(SidechainSettings.getStorage(walletBoxStore));
        bind(Storage.class)
                .annotatedWith(Names.named("WalletTransactionStorage"))
                .toInstance(SidechainSettings.getStorage(walletTransactionStore));
        bind(Storage.class)
                .annotatedWith(Names.named("StateStorage"))
                .toInstance(SidechainSettings.getStorage(stateStore));
        bind(Storage.class)
                .annotatedWith(Names.named("HistoryStorage"))
                .toInstance(SidechainSettings.getStorage(historyStore));
    }
}
