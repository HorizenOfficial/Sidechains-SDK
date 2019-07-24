package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.Map;

public interface NodeMemoryPool {

    Map<String, BoxTransaction<? extends Proposition, ? extends Box<?>> > getMemoryPool();

    Iterable<BoxTransaction<? extends Proposition, ? extends Box<?>>> getMemoryPoolSortedByFee(int limit);

    int getMemoryPoolSize();
}
