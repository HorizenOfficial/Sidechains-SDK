#!/usr/bin/env python3
import logging

from eth_typing import HexStr
from eth_utils import function_signature_to_4byte_selector, encode_hex, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, format_evm
from SidechainTestFramework.account.utils import FORGER_STAKE_SMART_CONTRACT_ADDRESS, PROXY_SMART_CONTRACT_ADDRESS
from test_framework.util import assert_equal, assert_false, assert_true

"""
Check contracts interoperability, i.e an EVM Contract calling a native contract or vice-versa.

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
    - Compile and deploy the Storage contract
    - Fetch current storage value directly via the Storage contract
    - Fetch current storage value via the proxy native contract 
    - Verify identical results
"""


class SCEvmNativeInterop(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=100)

    def deploy(self, contract_name, *args):
        logging.info(f"Creating smart contract utilities for {contract_name}")
        contract = SmartContract(contract_name)
        logging.info(contract)
        contract_address = deploy_smart_contract(self.sc_nodes[0], contract, self.evm_address, *args)
        return contract, contract_address

    def run_test(self):
        self.sc_ac_setup()
        node = self.sc_nodes[0]

        """
        Tests from EVM Smart contract to Native Smart contract
        """

        # Compile and deploy the NativeInterop contract
        # d9908c86: GetForgerStakes()
        # 3ef7a7c9: GetForgerStakesCallCode()
        # 585e290d: GetForgerStakesDelegateCall()
        _, contract_address = self.deploy("NativeInterop")

        NATIVE_INTEROP_GETFORGERSTAKES_SIG = "0xd9908c86"
        # Fetch all forger stakes via the NativeInterop contract
        actual_value = node.rpc_eth_call(
            {
                "to": contract_address,
                "input": NATIVE_INTEROP_GETFORGERSTAKES_SIG
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
        assert_true("unsupported call method" in delegate_call_result["error"]["message"])

        # Verify CALLCODE to a native contract throws an error
        call_code_result = node.rpc_eth_call(
            {
                "to": contract_address,
                "input": "0x3ef7a7c9"
            }, "latest"
        )
        assert_true("error" in call_code_result)
        assert_true("unsupported call method" in call_code_result["error"]["message"])

        # Verify tracing gives reasonable result for the call from EVM contract to native contract
        trace_response = node.rpc_debug_traceCall(
            {
                "to": contract_address,
                "input": NATIVE_INTEROP_GETFORGERSTAKES_SIG
            }, "latest", {
                "tracer": "callTracer"
            }
        )
        assert_false("error" in trace_response)
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
        #     }
        #   ]
        # }

        assert_equal(contract_address.lower(), trace_result["to"].lower())
        assert_equal(1, len(trace_result["calls"]))
        assert_equal(NATIVE_INTEROP_GETFORGERSTAKES_SIG, trace_result["input"])
        native_call = trace_result["calls"][0]
        assert_equal("STATICCALL", native_call["type"])
        assert_equal(contract_address.lower(), native_call["from"].lower())
        assert_equal("0x" + FORGER_STAKE_SMART_CONTRACT_ADDRESS, native_call["to"])
        assert_true(int(native_call["gas"], 16) > 0)
        assert_true(int(native_call["gasUsed"], 16) > 0)
        assert_equal("0xf6ad3c23", native_call["input"])
        assert_true(len(native_call["output"]) > 512)
        assert_false("calls" in native_call)

        # Get gas estimations
        estimation_interop = node.rpc_eth_estimateGas(
            {
                "to": contract_address,
                "input": NATIVE_INTEROP_GETFORGERSTAKES_SIG
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

        """
        Tests from Native Smart contract to EVM Smart contract
        """
        # Compile and deploy the Storage contract
        initial_value = 142
        storage_contract, storage_contract_address = self.deploy("Storage", initial_value)

        method_inc = 'inc()'
        method_retrieve = 'retrieve()'

        sol_contract_call_data_inc = storage_contract.raw_encode_call(method_inc)
        sol_contract_call_data_retrieve = storage_contract.raw_encode_call(method_retrieve)

        # Get current value in Storage by calling the EVM contract directly
        expected_value = node.rpc_eth_call(
            {
                "to": storage_contract_address,
                "input": sol_contract_call_data_retrieve
            }, "latest"
        )
        value = int(expected_value['result'], 16)
        assert_equal(initial_value, value)

        # Get current value in Storage by calling the proxy native contract

        method = 'invokeCall(address,bytes)'
        abi_str = function_signature_to_4byte_selector(method)
        encoded_abi_method_signature = encode_hex(abi_str)
        addr_padded_str = "000000000000000000000000" + remove_0x_prefix(storage_contract_address)
        data_input = encoded_abi_method_signature + addr_padded_str
        data_input += "0000000000000000000000000000000000000000000000000000000000000040"
        h_len = hex(len(remove_0x_prefix(sol_contract_call_data_retrieve)) // 2)
        data_input += "000000000000000000000000000000000000000000000000000000000000000" + remove_0x_prefix(HexStr(h_len))
        data_input += remove_0x_prefix(sol_contract_call_data_retrieve)
        data_input += "00000000000000000000000000000000000000000000000000000000"

        native_contract_address = format_evm(PROXY_SMART_CONTRACT_ADDRESS)
        expected_value = node.rpc_eth_call(
            {
                "to": native_contract_address,
                "input": data_input
            }, "latest"
        )

        value = int(expected_value['result'], 16)
        assert_equal(initial_value, value)

        # Verify tracing
        trace_response = node.rpc_debug_traceCall(
            {
                "to": native_contract_address,
                "input": data_input
            }, "latest", {
                "tracer": "callTracer"
            }
        )
        assert_false("error" in trace_response)
        assert_true("result" in trace_response)
        trace_result = trace_response["result"]
        logging.info("trace result: {}".format(trace_result))

        assert_equal(native_contract_address.lower(), trace_result["to"].lower())
        assert_equal(1, len(trace_result["calls"]))
        assert_equal(data_input.lower(), trace_result["input"].lower())
        evm_call = trace_result["calls"][0]
        assert_equal("CALL", evm_call["type"])
        assert_equal(native_contract_address.lower(), evm_call["from"].lower())
        assert_equal(storage_contract_address.lower(), evm_call["to"].lower())
        assert_true(int(native_call["gas"], 16) > 0)
        assert_true(int(native_call["gasUsed"], 16) > 0)
        assert_equal(sol_contract_call_data_retrieve.lower(), evm_call["input"].lower())
        output = int(evm_call["output"], 16)
        assert_equal(initial_value, output)
        assert_false("calls" in evm_call)

        # Get gas estimations
        estimation_interop = node.rpc_eth_estimateGas(
            {
                "to": native_contract_address,
                "input": data_input
            }
        )
        estimation_evm = node.rpc_eth_estimateGas(
            {
                "to": storage_contract_address,
                "input": sol_contract_call_data_retrieve
            }
        )
        logging.info("estimated gas interop: {}".format(estimation_interop))
        logging.info("estimated gas evm: {}".format(estimation_evm))

        # Gas usage given in a trace does not include intrinsic gas, we need to add it to compare with gas estimation
        # 21k + (number of non-zero bytes in the input) * 16 + (number of zero bytes) * 4
        intrinsic_gas = 21000 + 4 * 16

        # Verify gas usage reported by the trace matches with the estimated gas for a call to the EVM contract
        assert_equal(int(trace_result["gasUsed"], 16), int(estimation_interop["result"], 16))

        # Verify gas usage of the nested call to the native contract reported by the trace matches with the estimation
        assert_equal(int(evm_call["gasUsed"], 16) + intrinsic_gas, int(estimation_evm["result"], 16))




        # Verify STATICCALL to a readwrite EVM contract method throws an error
        method = 'invokeStaticCall(address,bytes)'
        abi_str = function_signature_to_4byte_selector(method)
        encoded_abi_method_signature = encode_hex(abi_str)
        addr_padded_str = "000000000000000000000000" + remove_0x_prefix(storage_contract_address)
        data_input_failed = encoded_abi_method_signature + addr_padded_str
        data_input_failed += "0000000000000000000000000000000000000000000000000000000000000040"
        h_len = hex(len(remove_0x_prefix(sol_contract_call_data_inc)) // 2)
        data_input_failed += "000000000000000000000000000000000000000000000000000000000000000" + remove_0x_prefix(HexStr(h_len))
        data_input_failed += remove_0x_prefix(sol_contract_call_data_inc)
        data_input_failed += "00000000000000000000000000000000000000000000000000000000"

        result = node.rpc_eth_call(
            {
                "to": native_contract_address,
                "input": data_input_failed
            }, "latest"
        )

        assert_true('error' in result)
        assert_true('write protection' in result['error']['message'])

        # TODO traceCall seems to have problems with failed txs. Needs to check what is the correct behavior
        # # Verify tracing
        # trace_response = node.rpc_debug_traceCall(
        #     {
        #         "to": native_contract_address,
        #         "input": data_input_failed
        #     }, "latest", {
        #         "tracer": "callTracer"
        #     }
        # )
        # logging.info("trace result: {}".format(trace_response))
        # assert_false("error" in trace_response)
        # assert_true("result" in trace_response)
        # trace_result = trace_response["result"]
        # logging.info("trace result: {}".format(trace_result))
        #
        # assert_equal(native_contract_address.lower(), trace_result["to"].lower())
        # assert_equal(1, len(trace_result["calls"]))
        # assert_equal(data_input_failed.lower(), trace_result["input"].lower())
        # assert_equal("write protection", trace_result["error"])
        # evm_call = trace_result["calls"][0]
        # assert_equal("STATICCALL", evm_call["type"])
        # assert_equal(native_contract_address.lower(), evm_call["from"].lower())
        # assert_equal(storage_contract_address.lower(), evm_call["to"].lower())
        # assert_true(int(native_call["gas"], 16) > 0)
        # assert_true(int(native_call["gasUsed"], 16) > 0)
        # assert_equal(sol_contract_call_data_inc.lower(), evm_call["input"].lower())
        # assert_equal("write protection", evm_call["error"])
        # assert_false("calls" in evm_call)



if __name__ == "__main__":
    SCEvmNativeInterop().main()
