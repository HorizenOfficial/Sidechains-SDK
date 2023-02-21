package com.horizen.evm.results;

import com.horizen.evm.Address;
import com.horizen.evm.Hash;

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
        Address address,
        String[] accountProof,
        BigInteger balance,
        Hash codeHash,
        BigInteger nonce,
        Hash storageHash,
        ProofStorageResult[] storageProof
    ) {
        this.address = address;
        this.accountProof = Objects.requireNonNullElseGet(accountProof, () -> new String[0]);
        this.balance = balance;
        this.codeHash = codeHash;
        this.nonce = nonce;
        this.storageHash = storageHash;
        this.storageProof = Objects.requireNonNullElseGet(storageProof, () -> new ProofStorageResult[0]);
    }

    public static class ProofStorageResult {
        public final String key;
        public final BigInteger value;
        public final String[] proof;

        public ProofStorageResult(String key, BigInteger value, String[] proof) {
            this.key = key;
            this.value = value;
            this.proof = proof;
        }
    }
}
