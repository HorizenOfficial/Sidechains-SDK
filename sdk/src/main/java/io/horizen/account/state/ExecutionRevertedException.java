package io.horizen.account.state;

/**
 * Message processing failed, also revert-and-keep-gas-left.
 */
public class ExecutionRevertedException extends ExecutionFailedException {
    public final byte[] revertReason;

    public ExecutionRevertedException(byte[] revertReason) {
        super("execution reverted");
        this.revertReason = revertReason;
    }

    public ExecutionRevertedException(String message) {
        super(message);
        this.revertReason = new byte[0];
    }

    public ExecutionRevertedException(String message, Throwable cause) {
        super(message, cause);
        this.revertReason = new byte[0];
    }
}
