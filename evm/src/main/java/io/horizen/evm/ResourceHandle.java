package io.horizen.evm;

public abstract class ResourceHandle implements AutoCloseable {
    /**
     * Handle to a native resource that requires manual release.
     */
    final int handle;

    public ResourceHandle(int handle) {
        this.handle = handle;
    }

    @Override
    public String toString() {
        return String.format("ResourceHandle{handle=%d}", handle);
    }
}
