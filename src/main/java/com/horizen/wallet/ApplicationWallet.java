package com.horizen.wallet;

import java.util.List;

import com.horizen.secret.Secret;
import com.horizen.box.Box;
import com.horizen.utils.ByteArrayWrapper;

public interface ApplicationWallet {

    void onAddSecret(Secret secret);
    void onRemoveSecret(Secret secret);
    void onNewBox(List<Box> boxes);
    void onRemoveBox(List<ByteArrayWrapper> boxIds);
}
