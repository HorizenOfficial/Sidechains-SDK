package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.ProofAccountResult;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class EthereumAccountProofView {
    public final Address address;
    public final String[] accountProof;
    public final String balance;
    public final Hash codeHash;
    public final String nonce;
    public final Hash storageHash;
    public final EthereumStorageProof[] storageProof;

    @JsonView(Views.Default.class)
    public static class EthereumStorageProof {
        public final String key;
        public final String value;
        public final String[] proof;

        public EthereumStorageProof(ProofAccountResult.ProofStorageResult result) {
            this.key = result.key;
            this.value = Numeric.encodeQuantity(result.value);
            this.proof = result.proof;
        }
    }

    public EthereumAccountProofView(ProofAccountResult result) {
        this.address = result.address;
        this.accountProof = result.accountProof;
        this.balance = Numeric.encodeQuantity(result.balance);
        this.codeHash = result.codeHash;
        this.nonce = Numeric.encodeQuantity(result.nonce);
        this.storageHash = result.storageHash;
        this.storageProof = result.storageProof == null ? null : Arrays
            .stream(result.storageProof)
            .map(EthereumStorageProof::new)
            .toArray(EthereumStorageProof[]::new);
    }
}
