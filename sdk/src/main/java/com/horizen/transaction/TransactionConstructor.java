package com.horizen.transaction;

import com.horizen.proof.Proof;

import java.util.List;

@FunctionalInterface
public interface TransactionConstructor
{
  SidechainTransaction apply(List<byte[]> inputIds,
                                 List<OutputDataSource> dataSources,
                                 List<Proof> proofs,
                                 long fee,
                                 long timestamp,
                                 Object additionalData);
}
