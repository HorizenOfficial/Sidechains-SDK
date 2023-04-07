package io.horizen.cryptolibprovider;

public enum CustomFieldsReservedPositions {

    Sc2sc_message_tree_root((byte)1),
    Sc2sc_previous_certificate_hash((byte)2);

    private final int position;

    CustomFieldsReservedPositions(int position) {
        this.position = position;
    }

    public int position() {
        return position;
    }
}
