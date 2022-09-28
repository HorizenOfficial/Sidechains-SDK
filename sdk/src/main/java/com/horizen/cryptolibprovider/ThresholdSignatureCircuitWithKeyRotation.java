package com.horizen.cryptolibprovider;

import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.utils.Pair;
import scala.collection.Seq;

import java.util.List;

public interface ThresholdSignatureCircuitWithKeyRotation {
    Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                   byte[] sidechainId,
                                   int epochNumber,
                                   byte[] endCumulativeScTxCommTreeRoot,
                                   long btrFee,
                                   long ftMinAmount,
                                   Seq<byte[]> customParameters,
                                   Seq<byte[]> signingKeySignatures,
                                   Seq<byte[]> masterKeySignatures,
                                   Seq<byte[]> newKeySignatures,
                                   WithdrawalEpochCertificate previousCertificate,
                                   long threshold,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk); // map fields to createProof of Cryptolib
}
