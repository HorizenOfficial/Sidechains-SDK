#!/usr/bin/env python3
import logging
import pprint

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract
from SidechainTestFramework.account.utils import FORGER_STAKE_SMART_CONTRACT_ADDRESS
from test_framework.util import assert_equal, assert_false, assert_true

"""
Check an EVM Contract calling a native contract.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Compile and deploy the NativeInterop contract
    - Fetch all forger stakes via the NativeInterop contract
    - Fetch all forger stakes by calling the native contract directly
    - Verify identical results
"""


class SCEvmNativeInterop(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=100)

    def deploy(self, contract_name):
        logging.info(f"Creating smart contract utilities for {contract_name}")
        contract = SmartContract(contract_name)
        logging.info(contract)
        contract_address = deploy_smart_contract(self.sc_nodes[0], contract, self.evm_address)
        return contract, contract_address

    def run_test(self):
        self.sc_ac_setup()
        node = self.sc_nodes[0]

        # Compile and deploy the NativeInterop contract
        # d9908c86: GetForgerStakes()
        # 3ef7a7c9: GetForgerStakesCallCode()
        # 585e290d: GetForgerStakesDelegateCall()
        _, contract_address = self.deploy("NativeInterop")

        # Fetch all forger stakes via the NativeInterop contract
        actual_value = node.rpc_eth_call(
            {
                "to": contract_address,
                "input": "0xd9908c86"
            }, "latest"
        )

        # Fetch all forger stakes by calling the native contract directly
        expected_value = node.rpc_eth_call(
            {
                "to": "0x" + FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                "input": "0xf6ad3c23"
            }, "latest"
        )

        # Verify identical results
        assert_equal(expected_value, actual_value, "results do not match")

        # Verify DELEGATECALL to a native contract throws an error
        delegate_call_result = node.rpc_eth_call(
            {
                "to": contract_address,
                "input": "0x585e290d"
            }, "latest"
        )
        assert_true("error" in delegate_call_result)
        #assert_true("unsupported call method" in delegate_call_result["error"]["message"])

        # Verify CALLCODE to a native contract throws an error
        call_code_result = node.rpc_eth_call(
            {
                "to": contract_address,
                "input": "0x3ef7a7c9"
            }, "latest"
        )
        #assert_true("error" in call_code_result)
        #assert_true("unsupported call method" in call_code_result["error"]["message"])

        # Verify tracing gives reasonable result for the call from EVM contract to native contract
        trace_response = node.rpc_debug_traceCall(
            {
                "to": contract_address,
                "input": "0xd9908c86"
            }, "latest", {
                "tracer": "callTracer"
            }
        )
        assert_false("error" in trace_response)
        pprint.pprint(trace_response)
        assert_true("result" in trace_response)
        trace_result = trace_response["result"]
        logging.info("trace result: {}".format(trace_result))

        # Expected output
        # {
        #   "type": "CALL",
        #   "from": "0x0000000000000000000000000000000000000000",
        #   "to": "0x840463d17b8c7833883eaa47d23b2646f7fd1fd9",
        #   "value": "0x0",
        #   "gas": "0x2fa9e38",
        #   "gasUsed": "0x6b9e",
        #   "input": "0xd9908c86",
        #   "output": "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000013941b55d40a2cac0485248eca396e72237d9ca08e07f686989ffff37f1d320960000000000000000000000000000000000000000000000056bc75e2d63100000000000000000000000000000375ea7214743b3ad892beed86999a1f5a6794ad76e3bda4dfddf67e293362514c36142f70862dab22cd3609face526aec9b1c809dbfb30791dbc1b1d0140fea9c49cd2ca0d6aade8139ee919cc4795e11ae9c1040000000000000000000000000000000000000000000000000000000000000000",
        #   "calls": [
        #     {
        #       "type": "STATICCALL",
        #       "from": "0x840463d17b8c7833883eaa47d23b2646f7fd1fd9",
        #       "to": "0x0000000000000000000022222222222222222222",
        #       "gas": "0x186a0",
        #       "gasUsed": "0x49d4",
        #       "input": "0xf6ad3c23",
        #       "output": "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000013941b55d40a2cac0485248eca396e72237d9ca08e07f686989ffff37f1d320960000000000000000000000000000000000000000000000056bc75e2d63100000000000000000000000000000375ea7214743b3ad892beed86999a1f5a6794ad76e3bda4dfddf67e293362514c36142f70862dab22cd3609face526aec9b1c809dbfb30791dbc1b1d0140fea9c49cd2ca0d6aade8139ee919cc4795e11ae9c1040000000000000000000000000000000000000000000000000000000000000000"
      #       }
        #   ]
        # }
        assert_equal(contract_address.lower(), trace_result["to"].lower())
        native_call = trace_result["calls"][0]
        assert_equal("STATICCALL", native_call["type"])
        assert_equal(contract_address.lower(), native_call["from"].lower())
        assert_equal("0x" + FORGER_STAKE_SMART_CONTRACT_ADDRESS, native_call["to"])
        assert_true(int(native_call["gas"], 16) > 0)
        assert_true(int(native_call["gasUsed"], 16) > 0)
        assert_equal("0xf6ad3c23", native_call["input"])
        assert_true(len(native_call["output"]) > 512)

        # Get gas estimations
        estimation_interop = node.rpc_eth_estimateGas(
            {
                "to": contract_address,
                "input": "0xd9908c86"
            }
        )
        estimation_native = node.rpc_eth_estimateGas(
            {
                "to": "0x" + FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                "input": "0xf6ad3c23"
            }
        )
        logging.info("estimated gas interop: {}".format(estimation_interop))
        logging.info("estimated gas native: {}".format(estimation_native))

        # Gas usage given in a trace does not include intrinsic gas, we need to add it to compare with gas estimation
        # 21k + (number of non-zero bytes in the input) * 16 + (number of zero bytes) * 4
        intrinsic_gas = 21000 + 4 * 16

        # Verify gas usage reported by the trace matches with the estimated gas for a call to the EVM contract
        assert_equal(int(trace_result["gasUsed"], 16) + intrinsic_gas, int(estimation_interop["result"], 16))

        # Verify gas usage of the nested call to the native contract reported by the trace matches with the estimation
        assert_equal(int(native_call["gasUsed"], 16) + intrinsic_gas, int(estimation_native["result"], 16))


if __name__ == "__main__":
    SCEvmNativeInterop().main()
