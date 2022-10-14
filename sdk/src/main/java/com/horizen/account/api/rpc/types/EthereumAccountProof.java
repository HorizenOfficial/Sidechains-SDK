package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.ProofAccountResult;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class EthereumAccountProof {
    public final String address;
    public final String[] accountProof;
    public final String balance;
    public final String codeHash;
    public final String nonce;
    public final String storageHash;
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

    public EthereumAccountProof(ProofAccountResult result) {
        this.address = result.address.toString();
        this.accountProof = result.accountProof;
        this.balance = Numeric.encodeQuantity(result.balance);
        this.codeHash = result.codeHash == null ? null : result.codeHash.toString();
        this.nonce = Numeric.encodeQuantity(result.nonce);
        this.storageHash = result.storageHash == null ? null : result.storageHash.toString();
        this.storageProof = result.storageProof == null ? null : Arrays
            .stream(result.storageProof)
            .map(EthereumStorageProof::new)
            .toArray(EthereumStorageProof[]::new);
    }
}
