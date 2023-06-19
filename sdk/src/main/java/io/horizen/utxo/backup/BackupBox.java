package io.horizen.utxo.backup;

import io.horizen.proposition.Proposition;
import io.horizen.utxo.box.Box;

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
