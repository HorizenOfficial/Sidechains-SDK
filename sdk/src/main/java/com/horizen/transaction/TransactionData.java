package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.proof.Proof;
import com.horizen.utils.Pair;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class TransactionData {
  private final List<OutputDataSource> outputDataSources;
  private final long fee;
  private final List<Pair<NoncedBox, Function<byte[], Proof>>> inputBoxesWithProofCreator;

  public TransactionData(List<OutputDataSource> outputDataSources, long fee)
  {
    this.outputDataSources = outputDataSources;
    this.fee = fee;
    this.inputBoxesWithProofCreator = Collections.emptyList();
  }

  public TransactionData(List<OutputDataSource> outputDataSources, long fee, List<Pair<NoncedBox, Function<byte[], Proof>>> inputBoxesWithProofCreator)
  {
    this.outputDataSources = outputDataSources;
    this.fee = fee;
    this.inputBoxesWithProofCreator = inputBoxesWithProofCreator;
  }

  public List<OutputDataSource> getOutputDataSources()
  {
    return outputDataSources;
  }

  public long getFee()
  {
    return fee;
  }

  public List<Pair<NoncedBox, Function<byte[], Proof>>> getInputBoxesWithProofCreator()
  {
    return inputBoxesWithProofCreator;
  }
}
