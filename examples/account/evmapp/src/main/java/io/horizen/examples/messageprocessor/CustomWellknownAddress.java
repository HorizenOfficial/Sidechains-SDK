package io.horizen.examples.messageprocessor;

import io.horizen.evm.Address;

// NOTE: when defining new contract addresses DO NOT override core ones in Sidechains-SDK/sdk/src/main/scala/io/horizen/account/utils/WellKnownAddresses.scala
public final class CustomWellknownAddress {
    public final static Address VOTE_SMART_CONTRACT_ADDRESS = new Address("0x0000000000000000000055555555555555555555");
    public final static Address REDEEM_VOTE_SMART_CONTRACT_ADDRESS = new Address("0x0000000000000000000066666666666666666666");
}
