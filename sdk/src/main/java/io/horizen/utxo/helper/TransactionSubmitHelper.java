package io.horizen.utxo.helper;

import io.horizen.helper.BaseTransactionSubmitHelper;
import io.horizen.utxo.box.Box;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.transaction.BoxTransaction;
import java.util.Optional;
import java.util.function.BiConsumer;

public interface TransactionSubmitHelper extends BaseTransactionSubmitHelper<BoxTransaction<Proposition, Box<Proposition>>> {

   @Override
   void submitTransaction(BoxTransaction<Proposition, Box<Proposition>> tx) throws IllegalArgumentException;
    @Override
   void asyncSubmitTransaction(BoxTransaction<Proposition, Box<Proposition>> tx, BiConsumer<Boolean,
            Optional<Throwable>> callback);
}

