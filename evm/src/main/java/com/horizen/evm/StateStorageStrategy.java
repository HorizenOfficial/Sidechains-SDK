package com.horizen.evm;

public enum StateStorageStrategy {
    /**
     * Raw 256bit key-value pair storage, requires all values to be exactly 32-bytes.
     * This will always access exactly one key-value pair.
     */
    RAW,

    /**
     * Allows arbitrary length values by internally splitting values into 32-byte chunks.
     * This will access as many key-value pairs as necessary to handle the given amount of data.
     */
    CHUNKED,
}
