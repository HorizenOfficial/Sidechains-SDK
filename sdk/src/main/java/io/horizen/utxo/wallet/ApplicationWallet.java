package io.horizen.utxo.wallet;

import java.util.List;

import com.horizen.utxo.backup.BoxIterator;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.utxo.box.Box;

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
