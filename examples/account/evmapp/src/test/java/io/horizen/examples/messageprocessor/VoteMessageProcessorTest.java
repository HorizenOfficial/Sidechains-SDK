package io.horizen.examples.messageprocessor;

import com.google.common.primitives.Bytes;
import io.horizen.account.sc2sc.AccountCrossChainMessage;
import io.horizen.account.state.*;
import io.horizen.evm.Address;
import io.horizen.examples.messageprocessor.decoder.SendVoteCmdInputDecoder;
import io.horizen.utils.BytesUtils;
import org.junit.Test;

import static io.horizen.account.abi.ABIUtil.getArgumentsFromData;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.*;

public class VoteMessageProcessorTest {
    private final BaseAccountStateView view = mock(BaseAccountStateView.class);
    private final GasPool gasPool = mock(GasPool.class);
    private final BlockContext blockContext = mock(BlockContext.class);
    @Test
    public void test() throws ExecutionFailedException {
        AccountCrossChainMessage ccMsg = new AccountCrossChainMessage(
                1,
                BytesUtils.fromHexString("00c8f107a09cd4f463afc2f1e6e5bf6022ad4600"),
                BytesUtils.fromHexString("00c8f107a09cd4f463afc2f1e6e5bf6022ad4600"),
                BytesUtils.fromHexString("00c8f107a09cd4f463afc2f1e6e5bf6022ad4600"),
                "8".getBytes(StandardCharsets.UTF_8)
        );
        byte[] data = Bytes.concat(BytesUtils.fromHexString(VoteMessageProcessor.SEND_VOTE), ccMsg.encode());
        String dataStringHes = BytesUtils.toHexString(data);

        VoteMessageProcessor processor = new VoteMessageProcessor(BytesUtils.fromHexString("00c8f107a09cd4f463afc2f1e6e5bf6022ad4600"));
        Message msg = getMessage(
                new Address(BytesUtils.fromHexString("00c8f107a09cd4f463afc2f1e6e5bf6022ad4600")),
                data,
                new Address(BytesUtils.fromHexString("00c8f107a09cd4f463afc2f1e6e5bf6022ad4600"))
        );
        byte[] argumentsByte = getArgumentsFromData(msg.getData());
        SendVoteCmdInputDecoder decoder = new SendVoteCmdInputDecoder();
        AccountCrossChainMessage accCCMsg = decoder.decode(argumentsByte);
        assertEquals(ccMsg, accCCMsg);
    }

    private Message getMessage(
            Address to,
            byte[] data,
            Address from
    ) {
        BigInteger gasPrice = BigInteger.ZERO;
        BigInteger gasFeeCap = BigInteger.valueOf(1000001);
        BigInteger gasTipCap = BigInteger.ZERO;
        BigInteger gasLimit = BigInteger.valueOf(500000);
        return new Message(
                from,
                Optional.ofNullable(to),
                gasPrice,
                gasFeeCap,
                gasTipCap,
                gasLimit,
                BigInteger.ZERO,
                BigInteger.ZERO,
                data,
                false
        );
    }
}