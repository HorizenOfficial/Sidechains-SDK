package com.horizen.customtypes;

import com.horizen.backup.BoxIterator;
import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
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
    public void onReindex() {

    }

    @Override
    public boolean checkStoragesVersion(byte[] blockId) { return  true; }

    @Override
    public void onBackupRestore(BoxIterator i) {

    }
}
