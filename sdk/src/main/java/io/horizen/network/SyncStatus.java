package io.horizen.network;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.json.Views;
import scala.math.BigInt;

import java.math.BigInteger;

@JsonView(Views.Default.class)
public class SyncStatus {

    @JsonIgnore
    public boolean syncStatus;
    public BigInteger currentBlock;
    public BigInteger startingBlock;
    public BigInteger highestBlock;

    public SyncStatus(boolean syncStatus) {
        this.syncStatus = syncStatus;
    }

    public SyncStatus(boolean syncStatus, BigInt currentBlock, BigInt startingBlock, BigInt highestBlock) {
        this.syncStatus = syncStatus;
        this.currentBlock = currentBlock.bigInteger();
        this.startingBlock = startingBlock.bigInteger();
        this.highestBlock = highestBlock.bigInteger();
    }
}