package com.horizen.wallet;

import java.util.List;

import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.box.Box;

public interface ApplicationWallet {

    void onAddSecret(Secret secret);
    void onRemoveSecret(Proposition proposition);
    void onChangeBoxes(byte[] blockId, List<Box<Proposition>> boxesToUpdate, List<byte[]> boxIdsToRemove);
    void onRollback(byte[] blockId);
}
