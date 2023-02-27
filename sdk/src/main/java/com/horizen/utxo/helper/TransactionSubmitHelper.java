package com.horizen.utxo.helper;

import com.horizen.helper.BaseTransactionSubmitHelper;
import com.horizen.utxo.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.utxo.transaction.BoxTransaction;
import java.util.Optional;
import java.util.function.BiConsumer;

public interface TransactionSubmitHelper extends BaseTransactionSubmitHelper<BoxTransaction<Proposition, Box<Proposition>>> {

   @Override
   void submitTransaction(BoxTransaction<Proposition, Box<Proposition>> tx) throws IllegalArgumentException;
    @Override
   void asyncSubmitTransaction(BoxTransaction<Proposition, Box<Proposition>> tx, BiConsumer<Boolean,
            Optional<Throwable>> callback);
}

