package com.horizen.wallet;

import java.util.List;

import com.horizen.companion.SidechainBoxesCompanion;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.box.Box;
import org.iq80.leveldb.DBIterator;

public interface ApplicationWallet {

    void onAddSecret(Secret secret);
    void onRemoveSecret(Proposition proposition);
    void onChangeBoxes(byte[] blockId, List<Box<Proposition>> boxesToUpdate, List<byte[]> boxIdsToRemove);
    void onRollback(byte[] blockId);
    void onApplicationRestore(SidechainBoxesCompanion sidechainBoxesCompanion, DBIterator i);
}
