package com.horizen.customtypes;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.wallet.ApplicationWallet;

import java.util.List;

public class CustomApplicationWallet implements ApplicationWallet {
    @Override
    public void onAddSecret(Secret secret) {

    }

    @Override
    public void onRemoveSecret(Proposition proposition) {

    }

    @Override
    public void onChangeBoxes(List<Box> boxesToUpdate, List<byte[]> boxIdsToRemove) {

    }
}
