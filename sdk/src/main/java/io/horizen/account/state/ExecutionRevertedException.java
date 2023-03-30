package io.horizen.account.state;

import org.web3j.utils.Numeric;

/**
 * Message processing failed, also revert-and-keep-gas-left.
 */
public class ExecutionRevertedException extends ExecutionFailedException {
    public final byte[] returnData;

    public ExecutionRevertedException(byte[] returnData) {
        super(String.format("execution reverted with return data \"%s\"", Numeric.toHexString(returnData)));
        this.returnData = returnData;
    }

    public ExecutionRevertedException(String message) {
        super(message);
        this.returnData = new byte[0];
    }

    public ExecutionRevertedException(String message, Throwable cause) {
        super(message, cause);
        this.returnData = new byte[0];
    }
}
