package com.horizen.customtypes;

import com.horizen.box.Box;
import com.horizen.companion.SidechainBoxesCompanion;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.storage.StorageIterator;
import com.horizen.wallet.ApplicationWallet;

import java.util.List;

public class DefaultApplicationWallet implements ApplicationWallet {

    @Override
    public void onAddSecret(Secret secret) {

    }

    @Override
    public void onRemoveSecret(Proposition proposition) {

    }

    @Override
    public void onChangeBoxes(byte[] blockId, List<Box<Proposition>> boxesToUpdate, List<byte[]> boxIdsToRemove) {

    }

    @Override
    public void onRollback(byte[] blockId) {

    }

    @Override
    public void onApplicationRestore(SidechainBoxesCompanion sidechainBoxesCompanion, StorageIterator i) {

    }
}
