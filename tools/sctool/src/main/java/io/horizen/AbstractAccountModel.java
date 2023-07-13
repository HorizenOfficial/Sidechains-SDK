package io.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import io.horizen.account.block.AccountBlock;
import io.horizen.account.block.AccountBlockHeader;
import io.horizen.account.companion.SidechainAccountTransactionsCompanion;
import io.horizen.account.fork.GasFeeFork;
import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.state.AccountStateView;
import io.horizen.account.state.MessageProcessor;
import io.horizen.account.state.MessageProcessorInitializationException;
import io.horizen.account.state.MessageProcessorUtil;
import io.horizen.account.storage.AccountStateMetadataStorageView;
import io.horizen.account.transaction.AccountTransaction;
import io.horizen.account.utils.AccountFeePaymentsUtils;
import io.horizen.account.utils.Bloom;
import io.horizen.account.utils.FeeUtils;
import io.horizen.account.utils.MainchainTxCrosschainOutputAddressUtil;
import io.horizen.block.MainchainBlockReference;
import io.horizen.block.MainchainBlockReferenceData;
import io.horizen.block.MainchainHeader;
import io.horizen.block.Ommer;
import io.horizen.consensus.ForgingStakeInfo;
import io.horizen.evm.Hash;
import io.horizen.evm.MemoryDatabase;
import io.horizen.evm.StateDB;
import io.horizen.params.NetworkParams;
import io.horizen.params.RegTestParams;
import io.horizen.proof.Proof;
import io.horizen.proof.VrfProof;
import io.horizen.proposition.Proposition;
import io.horizen.secret.PrivateKey25519;
import io.horizen.transaction.mainchain.SidechainCreation;
import io.horizen.utils.MerklePath;
import io.horizen.utils.TimeToEpochUtils;
import io.horizen.vrf.VrfOutput;
import scala.collection.Iterator;
import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

abstract public class AbstractAccountModel implements SidechainModel<AccountBlock> {
    private static final String model = "account";

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public AccountBlock buildScGenesisBlock(
            MainchainBlockReference mcRef,
            SidechainCreation sidechainCreation,
            JsonNode json,
            PrivateKey25519 key,
            VrfProof vrfProof,
            VrfOutput vrfOutput,
            MerklePath mp,
            NetworkParams params
    ) {
        byte blockVersion = AccountBlock.ACCOUNT_BLOCK_VERSION();
        // no fee payments expected for the genesis block
        byte[] feePaymentsHash = AccountFeePaymentsUtils.DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH();

        byte[] stateRoot;
        try {
            stateRoot = getGenesisStateRoot(mcRef, params);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Error: 'Could not get genesis state root: %s", e.getMessage()));
        }

        byte[] receiptsRoot = StateDB.EMPTY_ROOT_HASH.toBytes(); // empty root hash (no receipts)

        // taken from the creation cc out
        AddressProposition forgerAddress = new AddressProposition(
                MainchainTxCrosschainOutputAddressUtil.getAccountAddress(
                        sidechainCreation.getScCrOutput().address()));

        BigInteger baseFee = FeeUtils.INITIAL_BASE_FEE();

        BigInteger gasUsed = BigInteger.ZERO;

        BigInteger gasLimit = GasFeeFork.get(0).blockGasLimit();

        SidechainAccountTransactionsCompanion sidechainTransactionsCompanion = new SidechainAccountTransactionsCompanion(new HashMap<>());

        ForgingStakeInfo forgingStakeInfo = sidechainCreation.getAccountForgerStakeInfo();

        Bloom logsBloom = new Bloom();

        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        // Undocumented optional argument, that is used in STF to decrease genesis block timestamps
        // to be able to generate next sc blocks without delays.
        // can be used only in Regtest network
        int regtestBlockTimestampRewind = json.has("regtestBlockTimestampRewind") ? json.get("regtestBlockTimestampRewind").asInt() : 0;
        long timestamp = (params instanceof RegTestParams) ? currentTimeSeconds - regtestBlockTimestampRewind : currentTimeSeconds;

        List<MainchainBlockReferenceData> mainchainBlockReferencesData = Collections.singletonList(mcRef.data());
        List<MainchainHeader> mainchainHeadersData = Collections.singletonList(mcRef.header());

        return AccountBlock.create(
                params.sidechainGenesisBlockParentId(),
                blockVersion,
                timestamp,
                scala.collection.JavaConverters.collectionAsScalaIterableConverter(mainchainBlockReferencesData).asScala().toSeq(),
                scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<AccountTransaction<Proposition, Proof<Proposition>>>()).asScala().toSeq(),
                scala.collection.JavaConverters.collectionAsScalaIterableConverter(mainchainHeadersData).asScala().toSeq(),
                scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<Ommer<AccountBlockHeader>>()).asScala().toSeq(),
                key,
                forgingStakeInfo,
                vrfProof,
                vrfOutput,
                mp,
                feePaymentsHash,
                stateRoot,
                receiptsRoot,
                forgerAddress,
                baseFee,
                gasUsed,
                gasLimit,
                sidechainTransactionsCompanion,
                logsBloom,
                scala.Option.empty()
        ).get();
    }

    protected byte[] getGenesisStateRoot(MainchainBlockReference mcRef, NetworkParams params) throws MessageProcessorInitializationException {
        List<MainchainBlockReferenceData> mainchainBlockReferencesData = Collections.singletonList(mcRef.data());
        MainchainHeader mcHeader = mcRef.header();

        List<MessageProcessor> customMessageProcessors = getCustomMessageProcessors(params);
        int consensusEpochAtGenesisBlock = 0;

        Seq<MessageProcessor> messageProcessorSeq = MessageProcessorUtil.getMessageProcessorSeq(
                params,
                JavaConverters.asScalaBuffer(customMessageProcessors),
                consensusEpochAtGenesisBlock
        );

        AccountStateView view = getStateView(messageProcessorSeq);
        try (view) {

            // init all the message processors
            Iterator<MessageProcessor> iter = messageProcessorSeq.iterator();
            while (iter.hasNext()) {
                iter.next().init(view);
            }

            // apply sc creation output, this will call forger stake msg processor
            for (MainchainBlockReferenceData mcBlockRefData : mainchainBlockReferencesData) {
                view.applyMainchainBlockReferenceData(mcBlockRefData);
            }

            view.applyMainchainHeader(mcHeader);

            // get the state root after all state-changing operations
            return view.getIntermediateRoot();
        }
    }

    private AccountStateView getStateView(scala.collection.Seq<MessageProcessor> mps) {
        var dbm = new MemoryDatabase();
        StateDB stateDb = new StateDB(dbm, new Hash(AccountStateMetadataStorageView.DEFAULT_ACCOUNT_STATE_ROOT()));
        return new AccountStateView(null, stateDb, mps);
    }

    protected abstract List<MessageProcessor> getCustomMessageProcessors(NetworkParams params);
}
