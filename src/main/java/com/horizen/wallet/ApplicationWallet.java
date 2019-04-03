package com.horizen.wallet;

import java.util.List;

import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.box.Box;
import com.horizen.utils.ByteArrayWrapper;

public interface ApplicationWallet {

    void onAddSecret(Secret secret);
    void onRemoveSecret(Proposition proposition);
    void onChangeBoxes(List<Box> boxesToUpdate, List<byte[]> boxIdsToRemove);
}
