package com.horizen.account.state;

/**
 * Message processing failed, also revert-and-keep-gas-left.
 */
public class ExecutionRevertedException extends ExecutionFailedException {
    public final byte[] revertReason;

    public ExecutionRevertedException(byte[] revertReason) {
        super("execution reverted");
        this.revertReason = revertReason;
    }

    public ExecutionRevertedException() {
        this(new byte[0]);
    }
}
