import logging

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, format_eoa
from test_framework.util import hex_str_to_bytes


class SimpleProxyContract:

    def __init__(self, sc_node, evm_address):
        logging.info(f"Creating smart contract utilities for SimpleProxy")
        self.sc_node = sc_node
        self.contract = SmartContract("SimpleProxy")
        logging.info(self.contract)
        self.contract_address = deploy_smart_contract(sc_node, self.contract, evm_address, None, '', True)

    def do_call(self, target_addr, value, data):
        sol_contract_call_data_call = "doCall(address,uint256,bytes)"

        data_input = self.contract.raw_encode_call(sol_contract_call_data_call, target_addr, value,
                                                   data)
        result = self.sc_node.rpc_eth_call(
            {
                "to": self.contract_address,
                "input": data_input
            }, "latest"
        )

        raw_res = self.contract.raw_decode_call_result(sol_contract_call_data_call,
                                                       hex_str_to_bytes(format_eoa(result['result'])))
        return raw_res[0]

    def do_static_call(self, target_addr, data):
        sol_contract_call_data_call = "doStaticCall(address,bytes)"

        data_input = self.contract.raw_encode_call(sol_contract_call_data_call, target_addr,
                                                   data)
        result = self.sc_node.rpc_eth_call(
            {
                "to": self.contract_address,
                "input": data_input
            }, "latest"
        )

        raw_res = self.contract.raw_decode_call_result(sol_contract_call_data_call,
                                                       hex_str_to_bytes(format_eoa(result['result'])))
        return raw_res[0]
