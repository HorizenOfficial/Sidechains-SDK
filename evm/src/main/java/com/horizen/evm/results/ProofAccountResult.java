package com.horizen.evm.results;

import com.horizen.evm.Address;
import com.horizen.evm.Hash;

import java.math.BigInteger;

public class ProofAccountResult {
    public Address address;
    public String[] accountProof;
    public BigInteger balance;
    public Hash codeHash;
    public BigInteger nonce;
    public Hash storageHash;
    public ProofStorageResult[] storageProof;

    public ProofAccountResult() {
        accountProof = new String[0];
        storageProof = new ProofStorageResult[0];
    }

    public static class ProofStorageResult {
        public String key;
        public BigInteger value;
        public String[] proof;

        public ProofStorageResult() {
            proof = new String[0];
        }
    }
}
