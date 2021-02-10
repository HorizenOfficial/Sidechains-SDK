package com.horizen.helper;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;
import java.util.function.Consumer;

public interface TransactionSubmitHelper {

    public void submitTransaction(BoxTransaction<Proposition, Box<Proposition>> tx, Consumer<Boolean> callback);

}
