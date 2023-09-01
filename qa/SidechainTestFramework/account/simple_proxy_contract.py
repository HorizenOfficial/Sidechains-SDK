import logging

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, format_eoa
from test_framework.util import hex_str_to_bytes


class SimpleProxyContract:
    call_sig = "doCall(address,uint256,bytes)"
    static_call_sig = "doStaticCall(address,bytes)"

    def __init__(self, sc_node, evm_address):
        logging.info(f"Creating smart contract utilities for SimpleProxy")
        self.sc_node = sc_node
        self.contract = SmartContract("SimpleProxy")
        logging.info(self.contract)
        self.contract_address = deploy_smart_contract(sc_node, self.contract, evm_address, None, '', True)

    def do_call(self, target_addr, value, data):
        data_input = self.contract.raw_encode_call(self.call_sig, target_addr, value,
                                                   data)
        result = self.sc_node.rpc_eth_call(
            {
                "to": self.contract_address,
                "input": data_input
            }, "latest"
        )

        raw_res = self.contract.raw_decode_call_result(self.call_sig,
                                                       hex_str_to_bytes(format_eoa(result['result'])))
        return raw_res[0]

    def do_call_trace(self, target_addr, value, data: str):
        data_input = self.contract.raw_encode_call(self.call_sig, target_addr, value,
                                                   hex_str_to_bytes(data))
        result = self.sc_node.rpc_debug_traceCall(
            {
                "to": self.contract_address,
                "input": data_input
            }, "latest", {
                "tracer": "callTracer"
            }
        )

        return result


    def do_static_call(self, target_addr, data):
        data_input = self.contract.raw_encode_call(self.static_call_sig, target_addr,
                                                   data)
        result = self.sc_node.rpc_eth_call(
            {
                "to": self.contract_address,
                "input": data_input
            }, "latest"
        )

        raw_res = self.contract.raw_decode_call_result(self.static_call_sig,
                                                       hex_str_to_bytes(format_eoa(result['result'])))
        return raw_res[0]


    def do_static_call_trace(self, target_addr, data):
        data_input = self.contract.raw_encode_call(self.static_call_sig, target_addr,
                                                   data)
        result = self.sc_node.rpc_debug_traceCall(
            {
                "to": self.contract_address,
                "input": data_input
            }, "latest", {
                "tracer": "callTracer"
            }
        )

        return result

    def call_transaction(self, from_add, nonce: int, target_addr, value, data: str):
        tx_id = self.contract.call_function(self.sc_node, self.call_sig, target_addr,
                                            value, hex_str_to_bytes(data), fromAddress=from_add,
                                            toAddress=format_eoa(self.contract_address), nonce=nonce,
                                            gasLimit=30000000
                                            )

        return tx_id

    def estimate_gas(self, from_add, nonce: int, target_addr, value, data: str):
        data_input = self.contract.raw_encode_call(self.call_sig, target_addr, value,
                                                   hex_str_to_bytes(data))

        request = {
            "from": "0x" + from_add,
            "to": self.contract_address,
            "data": data_input,
            "nonce": nonce,
            "gas": "0x1C9C380"
        }

        return self.sc_node.rpc_eth_estimateGas(request)
