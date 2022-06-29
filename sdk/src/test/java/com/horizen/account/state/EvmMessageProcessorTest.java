package com.horizen.account.state;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.Arrays;

public class EvmMessageProcessorTest extends MessageProcessorTestBase {
    public void testInit() {
        var mockStateView = Mockito.mock(AccountStateView.class);
        var processor = new EvmMessageProcessor();
        // just make sure this does not throw
        processor.init(mockStateView);
    }

    public void testCanProcess() {
        var mockStateView = Mockito.mock(AccountStateView.class);
        var processor = new EvmMessageProcessor();

        Mockito.when(mockStateView.isSmartContractAccount(ArgumentMatchers.any(byte[].class))).thenAnswer(args -> {
            byte[] addressBytes = args.getArgument(0);
            assertNotNull("should not check the null address", addressBytes);
            return Arrays.equals(addressBytes, contractAddress);
        });

        assertTrue("should process smart contract deployment", processor.canProcess(getMessage(null), mockStateView));
        assertTrue(
                "should process calls to existing smart contracts",
                processor.canProcess(getMessage(contractAddress), mockStateView)
        );
        assertFalse(
                "should not process EOA to EOA transfer (empty account)",
                processor.canProcess(getMessage(emptyAddress), mockStateView)
        );
        assertFalse(
                "should not process EOA to EOA transfer (non-empty account)",
                processor.canProcess(getMessage(eoaAddress), mockStateView)
        );
        assertFalse(
                "should ignore data on EOA to EOA transfer",
                processor.canProcess(
                        getMessage(eoaAddress, "the same thing we do every night, pinky".getBytes()),
                        mockStateView
                )
        );
    }
}
