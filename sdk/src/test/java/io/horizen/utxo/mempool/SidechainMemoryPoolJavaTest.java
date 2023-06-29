package io.horizen.utxo.mempool;

import io.horizen.MempoolSettings;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.CrossChainMessageBox;
import io.horizen.utxo.box.WithdrawalRequestBox;
import io.horizen.utxo.transaction.BoxTransaction;
import io.horizen.utxo.transaction.RegularTransaction;
import io.horizen.utxo.transaction.TransactionIncompatibilityChecker;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

public class SidechainMemoryPoolJavaTest {
  @Test
  public void takeWithdrawalAndCrossChainBoxesWithLimitTest() {
    // Arrange
    SidechainMemoryPool memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0));
    RegularTransaction withdrawalTx = mock(RegularTransaction.class);
    List withdrawalBoxes = List.of(mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class));
    RegularTransaction crossChainTx = mock(RegularTransaction.class);
    List crossChainBoxes = List.of(mock(CrossChainMessageBox.class));

    Mockito.when(withdrawalTx.id()).thenReturn("firstTx");
    Mockito.when(crossChainTx.id()).thenReturn("secondTx");

    TransactionIncompatibilityChecker txIncChecker = mock(TransactionIncompatibilityChecker.class);
    Mockito.when(withdrawalTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(crossChainTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(txIncChecker.isMemoryPoolCompatible()).thenReturn(true);
    Mockito.when(txIncChecker.isTransactionCompatible(any(), any())).thenReturn(true);

    memoryPool.put(withdrawalTx);
    memoryPool.put(crossChainTx);

    Mockito.when(withdrawalTx.newBoxes()).thenReturn(withdrawalBoxes);
    Mockito.when(crossChainTx.newBoxes()).thenReturn(crossChainBoxes);

    // Act
    scala.collection.Iterable<BoxTransaction<Proposition, Box<Proposition>>> selectedTxs =
        memoryPool.takeWithdrawalAndCrossChainBoxesWithLimit(5, 5);

    // Assert
    assertEquals(memoryPool.size(), selectedTxs.size());
  }

  @Test
  public void takeWithdrawalAndCrossChainBoxesWithLimit_lowerBoundForWithdrawalBoxes() {
    // Arrange
    int allowedWithdrawalBoxes = 1;

    SidechainMemoryPool memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0));
    RegularTransaction firstTx = mock(RegularTransaction.class);
    List withdrawalBoxes = List.of(mock(WithdrawalRequestBox.class), mock(CrossChainMessageBox.class));
    RegularTransaction secondTx = mock(RegularTransaction.class);
    List crossChainBoxes = List.of(mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class), mock(CrossChainMessageBox.class));

    Mockito.when(firstTx.id()).thenReturn("firstTx");
    Mockito.when(secondTx.id()).thenReturn("secondTx");

    TransactionIncompatibilityChecker txIncChecker = mock(TransactionIncompatibilityChecker.class);
    Mockito.when(firstTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(secondTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(txIncChecker.isMemoryPoolCompatible()).thenReturn(true);
    Mockito.when(txIncChecker.isTransactionCompatible(any(), any())).thenReturn(true);

    memoryPool.put(firstTx);
    memoryPool.put(secondTx);

    Mockito.when(firstTx.newBoxes()).thenReturn(withdrawalBoxes);
    Mockito.when(secondTx.newBoxes()).thenReturn(crossChainBoxes);


    // Act
    scala.collection.Iterable<BoxTransaction<Proposition, Box<Proposition>>> selectedTxs =
        memoryPool.takeWithdrawalAndCrossChainBoxesWithLimit(allowedWithdrawalBoxes, 5);

    // Assert
    assertEquals(allowedWithdrawalBoxes, selectedTxs.size());
  }

  @Test
  public void takeWithdrawalAndCrossChainBoxesWithLimit_lowerBoundForCrossChainBoxes() {
    // Arrange
    int allowedCrossChainBoxes = 2;

    SidechainMemoryPool memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0));
    RegularTransaction firstTx = mock(RegularTransaction.class);
    List withdrawalBoxes = List.of(mock(CrossChainMessageBox.class), mock(WithdrawalRequestBox.class), mock(CrossChainMessageBox.class));
    RegularTransaction secondTx = mock(RegularTransaction.class);
    List crossChainBoxes = List.of(mock(WithdrawalRequestBox.class), mock(CrossChainMessageBox.class));

    Mockito.when(firstTx.id()).thenReturn("firstTx");
    Mockito.when(secondTx.id()).thenReturn("secondTx");

    TransactionIncompatibilityChecker txIncChecker = mock(TransactionIncompatibilityChecker.class);
    Mockito.when(firstTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(secondTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(txIncChecker.isMemoryPoolCompatible()).thenReturn(true);
    Mockito.when(txIncChecker.isTransactionCompatible(any(), any())).thenReturn(true);

    memoryPool.put(firstTx);
    memoryPool.put(secondTx);

    Mockito.when(firstTx.newBoxes()).thenReturn(withdrawalBoxes);
    Mockito.when(secondTx.newBoxes()).thenReturn(crossChainBoxes);


    // Act
    scala.collection.Iterable<BoxTransaction<Proposition, Box<Proposition>>> selectedTxs =
        memoryPool.takeWithdrawalAndCrossChainBoxesWithLimit(5, allowedCrossChainBoxes);

    // Assert
    assertEquals(1, selectedTxs.size());
  }

  @Test
  public void takeWithdrawalAndCrossChainBoxesWithLimit_takingAll() {
    // Arrange
    int allowedCrossChainBoxes = 2;

    SidechainMemoryPool memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0));
    RegularTransaction firstTx = mock(RegularTransaction.class);
    List withdrawalBoxes = List.of(mock(CrossChainMessageBox.class), mock(WithdrawalRequestBox.class), mock(CrossChainMessageBox.class));
    RegularTransaction secondTx = mock(RegularTransaction.class);
    List crossChainBoxes = List.of(mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class));

    Mockito.when(firstTx.id()).thenReturn("firstTx");
    Mockito.when(secondTx.id()).thenReturn("secondTx");

    TransactionIncompatibilityChecker txIncChecker = mock(TransactionIncompatibilityChecker.class);
    Mockito.when(firstTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(secondTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(txIncChecker.isMemoryPoolCompatible()).thenReturn(true);
    Mockito.when(txIncChecker.isTransactionCompatible(any(), any())).thenReturn(true);

    memoryPool.put(firstTx);
    memoryPool.put(secondTx);

    Mockito.when(firstTx.newBoxes()).thenReturn(withdrawalBoxes);
    Mockito.when(secondTx.newBoxes()).thenReturn(crossChainBoxes);


    // Act
    scala.collection.Iterable<BoxTransaction<Proposition, Box<Proposition>>> selectedTxs =
        memoryPool.takeWithdrawalAndCrossChainBoxesWithLimit(5, allowedCrossChainBoxes);

    // Assert
    assertEquals(allowedCrossChainBoxes, selectedTxs.size());
  }

  @Test
  public void takeWithdrawalAndCrossChainBoxesWithLimit_takingNone() {
    // Arrange
    int allowedCrossChainBoxes = 1;

    SidechainMemoryPool memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0));
    RegularTransaction firstTx = mock(RegularTransaction.class);
    List withdrawalBoxes = List.of(mock(CrossChainMessageBox.class), mock(WithdrawalRequestBox.class), mock(CrossChainMessageBox.class));
    RegularTransaction secondTx = mock(RegularTransaction.class);
    List crossChainBoxes = List.of(
        mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class), mock(WithdrawalRequestBox.class),
        mock(CrossChainMessageBox.class), mock(WithdrawalRequestBox.class), mock(CrossChainMessageBox.class)
    );

    Mockito.when(firstTx.id()).thenReturn("firstTx");
    Mockito.when(secondTx.id()).thenReturn("secondTx");

    TransactionIncompatibilityChecker txIncChecker = mock(TransactionIncompatibilityChecker.class);
    Mockito.when(firstTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(secondTx.incompatibilityChecker()).thenReturn(txIncChecker);
    Mockito.when(txIncChecker.isMemoryPoolCompatible()).thenReturn(true);
    Mockito.when(txIncChecker.isTransactionCompatible(any(), any())).thenReturn(true);

    memoryPool.put(firstTx);
    memoryPool.put(secondTx);

    Mockito.when(firstTx.newBoxes()).thenReturn(withdrawalBoxes);
    Mockito.when(secondTx.newBoxes()).thenReturn(crossChainBoxes);


    // Act
    scala.collection.Iterable<BoxTransaction<Proposition, Box<Proposition>>> selectedTxs =
        memoryPool.takeWithdrawalAndCrossChainBoxesWithLimit(5, allowedCrossChainBoxes);

    // Assert
    assertEquals(0, selectedTxs.size());
  }

  private MempoolSettings getMockedMempoolSettings(int maxSize, long minFeeRate) {
    MempoolSettings mockedSettings = mock(MempoolSettings.class);
    Mockito.when(mockedSettings.maxSize()).thenReturn(maxSize);
    Mockito.when(mockedSettings.minFeeRate()).thenReturn(minFeeRate);
    return mockedSettings;
  }
}