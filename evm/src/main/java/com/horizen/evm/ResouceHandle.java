package com.horizen.evm;

abstract class ResouceHandle implements AutoCloseable {
    /**
     * Handle to a native resource that requires manual release.
     */
    public final int handle;

    public ResouceHandle(int handle) {
        this.handle = handle;
    }

    @Override
    public String toString() {
        return String.format("ResouceHandle{handle=%d}", handle);
    }
}
