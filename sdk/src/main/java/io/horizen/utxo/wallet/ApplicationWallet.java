package io.horizen.utxo.wallet;

import io.horizen.proposition.Proposition;
import io.horizen.secret.Secret;
import io.horizen.utxo.backup.BoxIterator;
import io.horizen.utxo.box.Box;

import java.util.List;

public interface ApplicationWallet {

    void onAddSecret(Secret secret);
    void onRemoveSecret(Proposition proposition);
    void onChangeBoxes(byte[] blockId, List<Box<Proposition>> boxesToUpdate, List<byte[]> boxIdsToRemove);
    void onRollback(byte[] blockId);

    // check that all storages of the application which are update by the sdk core, have the version corresponding to the
    // blockId given. This is useful when checking the alignment of the storages versions at node restart
    boolean checkStoragesVersion(byte[] blockId);

    void onBackupRestore(BoxIterator i);
}
