package io.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import io.horizen.block.MainchainBlockReference;
import io.horizen.block.SidechainBlockBase;
import io.horizen.fork.ForkConfigurator;
import io.horizen.params.NetworkParams;
import io.horizen.proof.VrfProof;
import io.horizen.secret.PrivateKey25519;
import io.horizen.transaction.mainchain.SidechainCreation;
import io.horizen.utils.MerklePath;
import io.horizen.vrf.VrfOutput;

public interface SidechainModel<T extends SidechainBlockBase<?, ?>> {
    String getModelName();
    T buildScGenesisBlock(
            MainchainBlockReference mcRef,
            SidechainCreation sidechainCreation,
            JsonNode json,
            PrivateKey25519 key,
            VrfProof vrfProof,
            VrfOutput vrfOutput,
            MerklePath mp,
            NetworkParams params
    );
    ForkConfigurator getForkConfigurator();
}
