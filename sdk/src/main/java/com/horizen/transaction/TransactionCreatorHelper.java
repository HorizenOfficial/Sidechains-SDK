package com.horizen.transaction;

import com.horizen.box.*;
import com.horizen.box.data.NoncedBoxData;
import com.horizen.box.data.RegularBoxData;
import com.horizen.node.NodeMemoryPool;
import com.horizen.node.NodeWallet;
import com.horizen.node.SidechainNodeView;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TransactionCreatorHelper {

  TransactionCreatorHelper() {
  }

  //@TODO we need map boxType -> Function<byte[], Proof<P>> including custom boxes
  public List<Pair<NoncedBox, Function<byte[], Proof>>> createProofCreatorsForBoxes(SidechainNodeView sidechainNodeView, List<NoncedBox> boxes) {
    NodeWallet wallet = sidechainNodeView.getNodeWallet();
    List<Pair<NoncedBox, Function<byte[], Proof>>> boxWithProofCreator = boxes.stream().map(box -> {
      if (box instanceof RegularBox) {
        Function<byte[], Proof> proofCreator = messageToSign -> wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign);
        return new Pair<>(box, proofCreator);
      }
      else if (box instanceof ForgerBox) {
        Function<byte[], Proof> proofCreator = messageToSign -> wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign);
        return new Pair<>(box, proofCreator);
      }
      else {
        throw new IllegalArgumentException("Can't create proof creator for box");
      }
    }).collect(Collectors.toList());

    return boxWithProofCreator;
  }

  //return added boxes
  public List<NoncedBox> addRegularBoxesInputsForValue(TransactionData transactionData, long value, SidechainNodeView sidechainNodeView) {
    NodeMemoryPool memoryPool = sidechainNodeView.getNodeMemoryPool();
    NodeWallet nodeWallet = sidechainNodeView.getNodeWallet();

    List<byte[]> boxIdsToExclude =
        memoryPool.getTransactionsSortedByFee(memoryPool.getSize()).stream()
            .flatMap(tx -> tx.boxIdsToOpen().stream()).map(baw -> baw.data())
            .collect(Collectors.toList());

    long inputsTotalAmount = 0;

    //@TODO replace with List<? extends Box<? extends Proposition>>
    List<NoncedBox> inputRegularBoxes = new ArrayList<>();

    for (Box<Proposition> box : nodeWallet.boxesOfType(RegularBox.class, boxIdsToExclude)) {
      NoncedBox noncedBox = (NoncedBox) box;
      inputRegularBoxes.add(noncedBox);
      inputsTotalAmount += box.value();
      if (inputsTotalAmount > value) break;
    }

    if(inputsTotalAmount < value) {
      throw new IllegalArgumentException("Not enough balances in the wallet to create transaction.");
    }

    List<Pair<NoncedBox, Function<byte[], Proof>>> inputCoinBoxesWithProofCreator =
        createProofCreatorsForBoxes(sidechainNodeView, inputRegularBoxes);

    transactionData.getInputBoxesWithProofCreator().addAll(inputCoinBoxesWithProofCreator);

    return inputRegularBoxes;
  }

  public void sendChangeCoinsToAddress(TransactionData transactionData, PublicKey25519Proposition changeAddress) {
    long totalInputValue = transactionData.getInputBoxesWithProofCreator().stream().map(Pair::getKey).mapToLong(NoncedBox::value).sum();

    long outputValue = transactionData.getOutputDataSources().stream()
                        .flatMap(ds -> ds.getBoxData().stream())
                        .filter(boxData -> boxData instanceof CoinsBox)
                        .mapToLong(NoncedBoxData::value)
                        .sum();

    long totalRequiredValue = outputValue + transactionData.getFee();


    if (totalInputValue < totalRequiredValue) {
      throw new IllegalStateException("Can't create change due input value is less than output value");
    }

    if (totalInputValue > totalRequiredValue) {
      long change = totalInputValue - totalRequiredValue;

      RegularBoxData changeBoxData = new RegularBoxData(changeAddress, change);
      transactionData.getOutputDataSources().add(new BoxDataSource(changeBoxData));
    }
  }


  public void addRegularBoxesForValueWithChange(TransactionData transactionData,
                                                           long value,
                                                           SidechainNodeView sidechainNodeView) {
    List<NoncedBox> newAddedBoxes = addRegularBoxesInputsForValue(transactionData, value, sidechainNodeView);
    sendChangeCoinsToAddress(transactionData, (PublicKey25519Proposition) newAddedBoxes.get(0).proposition());
  }

  public SidechainTransaction createTransaction(TransactionData transactionData,
                                                SidechainNodeView sidechainNodeView,
                                                TransactionConstructor transactionConstructor,
                                                Object transactionAdditionalData) {
    List<byte[]> inputIds = transactionData.getInputBoxesWithProofCreator()
                                            .stream()
                                            .map(boxWithProofCreator -> boxWithProofCreator.getKey().id())
                                            .collect(Collectors.toList());

    long totalInputValue = transactionData.getInputBoxesWithProofCreator().stream()
        .map(Pair::getKey)
        .filter(bData -> bData instanceof CoinsBox)
        .mapToLong(NoncedBox::value)
        .sum();

    long outputValue = transactionData.getOutputDataSources().stream()
        .flatMap(ds -> ds.getBoxData().stream())
        .filter(bData -> bData instanceof CoinsBox)
        .mapToLong(NoncedBoxData::value)
        .sum();

    long totalOutputValue = outputValue + transactionData.getFee();

    if (totalInputValue != totalOutputValue) {
      throw new IllegalStateException("Input value for transaction is not equals to output value");
    }

    List<Proof> fakeProofs = Collections.nCopies(inputIds.size(), null);
    long timestamp = System.currentTimeMillis();

    SidechainTransaction unsignedTransaction =
        transactionConstructor.apply(inputIds, transactionData.getOutputDataSources(), fakeProofs,
            transactionData.getFee(), timestamp, transactionAdditionalData);

    // check here output boxes position in UTXO Merkle tree,
    // in case if some output position in Merkle tree is not free then
    // change transaction nonce and recreate transaction as many times as needed until all outputs take free position in UTXO merkle tree

    byte[] messageToSign = unsignedTransaction.messageToSign();

    List<Proof> inputBoxesProofs = transactionData.getInputBoxesWithProofCreator()
        .stream()
        .map(boxWithProofCreator -> {
          Function<byte[], Proof> proofCreator = boxWithProofCreator.getValue();
          return proofCreator.apply(messageToSign);
        })
        .collect(Collectors.toList());

    SidechainTransaction signedTransaction = transactionConstructor.apply(inputIds, transactionData.getOutputDataSources(), inputBoxesProofs,
        transactionData.getFee(), timestamp, transactionAdditionalData);

    return signedTransaction;
  }

}
