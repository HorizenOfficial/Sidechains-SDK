package io.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import io.horizen.block.MainchainBlockReference;
import io.horizen.block.Ommer;
import io.horizen.consensus.ForgingStakeInfo;
import io.horizen.cryptolibprovider.CircuitTypes;
import io.horizen.params.NetworkParams;
import io.horizen.params.RegTestParams;
import io.horizen.proof.VrfProof;
import io.horizen.proposition.Proposition;
import io.horizen.secret.PrivateKey25519;
import io.horizen.transaction.mainchain.SidechainCreation;
import io.horizen.utils.MerklePath;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.block.SidechainBlockHeader;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.ForgerBox;
import io.horizen.utxo.companion.SidechainTransactionsCompanion;
import io.horizen.utxo.transaction.SidechainTransaction;
import io.horizen.vrf.VrfOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

abstract public class AbstractUTXOModel implements SidechainModel<SidechainBlock> {
    private static final String model = "utxo";

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public SidechainBlock buildScGenesisBlock(
            MainchainBlockReference mcRef,
            SidechainCreation sidechainCreation,
            JsonNode json,
            PrivateKey25519 key,
            VrfProof vrfProof,
            VrfOutput vrfOutput,
            MerklePath mp,
            NetworkParams params
    ) {
        byte blockVersion = SidechainBlock.BLOCK_VERSION();
        // no fee payments expected for the genesis block
        byte[] feePaymentsHash = new byte[32];

        ForgerBox forgerBox = sidechainCreation.getBox();
        ForgingStakeInfo forgingStakeInfo = new ForgingStakeInfo(forgerBox.blockSignProposition(), forgerBox.vrfPubKey(), forgerBox.value());

        SidechainTransactionsCompanion sidechainTransactionsCompanion = new SidechainTransactionsCompanion(new HashMap<>(), CircuitTypes.NaiveThresholdSignatureCircuit());

        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        int regtestBlockTimestampRewind = json.has("regtestBlockTimestampRewind") ? json.get("regtestBlockTimestampRewind").asInt() : 0;
        long timestamp = (params instanceof RegTestParams) ? currentTimeSeconds - regtestBlockTimestampRewind : currentTimeSeconds;


        return SidechainBlock.create(
                params.sidechainGenesisBlockParentId(),
                blockVersion,
                timestamp,
                scala.collection.JavaConverters.collectionAsScalaIterableConverter(Collections.singletonList(mcRef.data())).asScala().toSeq(),
                scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<SidechainTransaction<Proposition, Box<Proposition>>>()).asScala().toSeq(),
                scala.collection.JavaConverters.collectionAsScalaIterableConverter(Collections.singletonList(mcRef.header())).asScala().toSeq(),
                scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<Ommer<SidechainBlockHeader>>()).asScala().toSeq(),
                key,
                forgingStakeInfo,
                vrfProof,
                mp,
                feePaymentsHash,
                sidechainTransactionsCompanion,
                scala.Option.empty()
        ).get();
    }
}
