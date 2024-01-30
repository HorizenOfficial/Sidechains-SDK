import logging

from eth_typing import HexStr
from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, format_eoa
from test_framework.util import hex_str_to_bytes


class SimpleProxyContract:
    call_sig = "doCall(address,uint256,bytes)"
    static_call_sig = "doStaticCall(address,bytes)"

    def __init__(self, sc_node, evm_address, use_shanghai=False):
        logging.info(f"Creating smart contract utilities for SimpleProxy")
        self.sc_node = sc_node

        if use_shanghai:
            self.contract = SmartContract("SimpleProxyShanghai")
        else:
            self.contract = SmartContract("SimpleProxy")
        logging.info(self.contract)
        self.contract_address = deploy_smart_contract(sc_node, self.contract, evm_address, None, '', True)

    def do_call(self, from_address, nonce, target_addr, value, data):
        if isinstance(data, str):
            data = hex_str_to_bytes(data)
        elif not isinstance(data, bytes):
            raise Exception("Only valid data types are byte arrays or string")
        data_input = self.contract.raw_encode_call(self.call_sig, target_addr, value,
                                                   data)
        result = self.sc_node.rpc_eth_call(
            {
                "to": self.contract_address,
                "from": add_0x_prefix(from_address),
                "nonce": nonce,
                "input": data_input
            }, "latest"
        )

        if "result" in result:
            raw_res = self.contract.raw_decode_call_result(self.call_sig,
                                                           hex_str_to_bytes(format_eoa(result['result'])))
            return raw_res[0]

        raise RuntimeError("Something went wrong, see {}".format(str(result)))

    def do_call_trace(self, from_address, nonce, target_addr, value, data):
        if isinstance(data, str):
            data = hex_str_to_bytes(data)
        elif not isinstance(data, bytes):
            raise Exception("Only valid data types are byte arrays or string")
        data_input = self.contract.raw_encode_call(self.call_sig, target_addr, value,
                                                   data)
        result = self.sc_node.rpc_debug_traceCall(
            {
                "to": self.contract_address,
                "from": add_0x_prefix(from_address),
                "nonce": nonce,
                "input": data_input,
            }, "latest", {
                "tracer": "callTracer"
            }
        )

        return result

    def do_static_call(self, from_address, nonce, target_addr, data):
        if isinstance(data, str):
            data = hex_str_to_bytes(data)
        elif not isinstance(data, bytes):
            raise Exception("Only valid data types are byte arrays or string")
        data_input = self.contract.raw_encode_call(self.static_call_sig, target_addr, data)
        result = self.sc_node.rpc_eth_call(
            {
                "to": self.contract_address,
                "from": add_0x_prefix(from_address),
                "nonce": nonce,
                "input": data_input
            }, "latest"
        )

        if "result" in result:
            raw_res = self.contract.raw_decode_call_result(self.static_call_sig,
                                                           hex_str_to_bytes(format_eoa(result['result'])))
            return raw_res[0]

        raise RuntimeError("Something went wrong, see {}".format(str(result)))

    def do_static_call_trace(self, from_address, nonce, target_addr, data):
        if isinstance(data, str):
            data = hex_str_to_bytes(data)
        elif not isinstance(data, bytes):
            raise Exception("Only valid data types are byte arrays or string")
        data_input = self.contract.raw_encode_call(self.static_call_sig, target_addr,
                                                   data)
        result = self.sc_node.rpc_debug_traceCall(
            {
                "to": self.contract_address,
                "from": add_0x_prefix(from_address),
                "nonce": nonce,
                "input": data_input
            }, "latest", {
                "tracer": "callTracer"
            }
        )

        return result

    def call_transaction(self, from_add, nonce: int, target_addr, value, data, gas_limit=20000000):
        if isinstance(data, str):
            data = hex_str_to_bytes(data)
        elif not isinstance(data, bytes):
            raise Exception("Only valid data types are byte arrays or string")
        tx_id = self.contract.call_function(self.sc_node, self.call_sig, target_addr,
                                            value, data, fromAddress=from_add,
                                            toAddress=format_eoa(self.contract_address), nonce=nonce,
                                            gasLimit=gas_limit
                                            )

        return tx_id

    def estimate_gas(self, from_add, nonce: int, target_addr, value, data, gas_limit=20000000):
        if isinstance(data, str):
            data = hex_str_to_bytes(data)
        elif not isinstance(data, bytes):
            raise Exception("Only valid data types are byte arrays or string")
        data_input = self.contract.raw_encode_call(self.call_sig, target_addr, value,
                                                   data)

        request = {
            "from": add_0x_prefix(from_add),
            "to": self.contract_address,
            "data": data_input,
            "nonce": nonce,
            "gas": HexStr(hex(gas_limit))
        }

        return self.sc_node.rpc_eth_estimateGas(request)
