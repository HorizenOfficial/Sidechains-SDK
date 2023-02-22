package io.horizen.evm.results;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;

import java.math.BigInteger;
import java.util.Objects;

public class ProofAccountResult {
    public final Address address;
    public final String[] accountProof;
    public final BigInteger balance;
    public final Hash codeHash;
    public final BigInteger nonce;
    public final Hash storageHash;
    public final ProofStorageResult[] storageProof;

    public ProofAccountResult(
        @JsonProperty("address") Address address,
        @JsonProperty("accountProof") String[] accountProof,
        @JsonProperty("balance") BigInteger balance,
        @JsonProperty("codeHash") Hash codeHash,
        @JsonProperty("nonce") BigInteger nonce,
        @JsonProperty("storageHash") Hash storageHash,
        @JsonProperty("storageProof") ProofStorageResult[] storageProof
    ) {
        this.address = address;
        this.accountProof = Objects.requireNonNullElse(accountProof, new String[0]);
        this.balance = balance;
        this.codeHash = codeHash;
        this.nonce = nonce;
        this.storageHash = storageHash;
        this.storageProof = Objects.requireNonNullElse(storageProof, new ProofStorageResult[0]);
    }

    public static class ProofStorageResult {
        public final String key;
        public final BigInteger value;
        public final String[] proof;

        public ProofStorageResult(
            @JsonProperty("key") String key,
            @JsonProperty("value") BigInteger value,
            @JsonProperty("proof") String[] proof
        ) {
            this.key = key;
            this.value = value;
            this.proof = Objects.requireNonNullElse(proof, new String[0]);
        }
    }
}
