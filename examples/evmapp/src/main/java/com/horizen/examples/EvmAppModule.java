package com.horizen.examples;

import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.horizen.ChainInfo;
import com.horizen.SidechainSettings;
import com.horizen.account.AccountAppModule;
import com.horizen.account.state.EvmMessageProcessor;
import com.horizen.account.state.MessageProcessor;
import com.horizen.account.transaction.AccountTransaction;
import com.horizen.api.http.ApplicationApiGroup;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretSerializer;
import com.horizen.settings.SettingsReader;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class EvmAppModule extends AccountAppModule {
    private final SettingsReader settingsReader;

    public EvmAppModule(String userSettingsFileName) {
        this.settingsReader = new SettingsReader(userSettingsFileName, Optional.empty());
    }

    @Override
    public void configureApp() {

        SidechainSettings sidechainSettings = this.settingsReader.getSidechainSettings();

        HashMap<Byte, SecretSerializer<Secret>> customSecretSerializers = new HashMap<>();
        HashMap<Byte, TransactionSerializer<AccountTransaction<Proposition, Proof<Proposition>>>>
                customAccountTransactionSerializers = new HashMap<>();

        // Here I can add my custom rest api and/or override existing one
        List<ApplicationApiGroup> customApiGroups = new ArrayList<>();

        // Here I can reject some of existing API routes
        // Each pair consists of "group name" -> "route name"
        // For example new Pair("wallet, "allBoxes");
        List<Pair<String, String>> rejectedApiPaths = new ArrayList<>();

        ChainInfo chainInfo = new ChainInfo(1997, 1661, 7331);

        // Here I can add my custom logic to manage EthereumTransaction content.
        List<MessageProcessor> customMessageProcessors = new ArrayList<>();
        customMessageProcessors.add(new EvmMessageProcessor());

        bind(SidechainSettings.class)
                .annotatedWith(Names.named("SidechainSettings"))
                .toInstance(sidechainSettings);

        bind(new TypeLiteral<HashMap<Byte, SecretSerializer<Secret>>>() {})
                .annotatedWith(Names.named("CustomSecretSerializers"))
                .toInstance(customSecretSerializers);

        bind(new TypeLiteral<HashMap<Byte, TransactionSerializer<AccountTransaction<Proposition, Proof<Proposition>>>>>() {})
                .annotatedWith(Names.named("CustomAccountTransactionSerializers"))
                .toInstance(customAccountTransactionSerializers);

        bind(new TypeLiteral<List<ApplicationApiGroup>>() {})
                .annotatedWith(Names.named("CustomApiGroups"))
                .toInstance(customApiGroups);

        bind(new TypeLiteral<List<Pair<String, String>>>() {})
                .annotatedWith(Names.named("RejectedApiPaths"))
                .toInstance(rejectedApiPaths);

        bind(ChainInfo.class)
                .annotatedWith(Names.named("ChainInfo"))
                .toInstance(chainInfo);

        bind(new TypeLiteral<List<MessageProcessor>>() {})
                .annotatedWith(Names.named("CustomMessageProcessors"))
                .toInstance(customMessageProcessors);
    }
}
