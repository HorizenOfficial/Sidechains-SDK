package io.horizen.utxo.backup;

import com.horizen.utxo.box.Box;
import com.horizen.proposition.Proposition;

public class BackupBox {
    private Box<Proposition> box;
    private byte[] boxKey;
    private byte[] boxValue;

    public BackupBox(Box<Proposition> box, byte[] boxKey, byte[] boxValue) {
        this.box = box;
        this.boxKey = boxKey;
        this.boxValue = boxValue;
    }

    public byte getBoxTypeId() {
        return box.boxTypeId();
    }

    public Box<Proposition> getBox() {
        return box;
    }

    public byte[] getBoxKey() {
        return boxKey;
    }

    public byte[] getBoxValue() {
        return boxValue;
    }

}
