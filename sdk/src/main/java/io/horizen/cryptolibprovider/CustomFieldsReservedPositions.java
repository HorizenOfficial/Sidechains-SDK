package io.horizen.cryptolibprovider;

public enum CustomFieldsReservedPositions {

    SC2SC_MESSAGE_TREE_ROOT((byte)1),
    SC2SC_PREVIOUS_CERTIFICATE_HASH((byte)2);

    private final int position;

    CustomFieldsReservedPositions(int position) {
        this.position = position;
    }

    public int position() {
        return position;
    }
}
