#!/usr/bin/env python3
import json
import logging
import pprint

from eth_abi import decode
from eth_typing import HexStr
from eth_utils import add_0x_prefix, function_signature_to_4byte_selector, encode_hex, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, ac_invokeProxy, format_evm, format_eoa
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.createLegacyEIP155Transaction import \
    createLegacyEIP155Transaction
from SidechainTestFramework.account.utils import PROXY_SMART_CONTRACT_ADDRESS, INTEROPERABILITY_FORK_EPOCH
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block, SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import assert_equal, assert_false, assert_true, hex_str_to_bytes, fail

"""
Check the Proxy native contract calling an EVM contract and also itself.

 This test doesn't support --allforks.
 
Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Test using a Proxy native smart contract for invoking a solidity smart contract
    - TODO: test Proxy nsc calling another native smart contract
"""


def get_contract_input_data_from_mempool_tx(sc_node, tx_hash):
    input_data = None
    mempool_list = sc_node.transaction_allTransactions(json.dumps({"format": True}))['result']['transactions']
    for tx in mempool_list:
        if tx['id'] == tx_hash:
            input_data = tx['data']
    return input_data


NUM_OF_RECURSIONS = 10

# The activation epoch of the Contracts Interoperability feature, as coded in the sdk


class SCEvmProxyNsc(AccountChainSetup):

    def __init__(self):
        super().__init__(block_timestamp_rewind=1500 * EVM_APP_SLOT_TIME * INTEROPERABILITY_FORK_EPOCH,
                         withdrawalEpochLength=100, max_account_slots=NUM_OF_RECURSIONS + 1,
                         max_nonce_gap=2 * NUM_OF_RECURSIONS + 1)

    def deploy(self, contract_name):
        logging.info(f"Creating smart contract utilities for {contract_name}")
        contract = SmartContract(contract_name)
        logging.info(contract)
        contract_address = deploy_smart_contract(self.sc_nodes[0], contract, self.evm_address)
        return contract, contract_address

    def run_test(self):
        if self.options.all_forks:
            logging.info("This test cannot be executed with --allforks")
            exit()

        self.sc_ac_setup()
        sc_node = self.sc_nodes[0]

        native_contract_address = PROXY_SMART_CONTRACT_ADDRESS

        # send funds to native smart contract before the fork is reached
        eoa_nsc_amount = 123456
        tx_hash_eoa = createLegacyEIP155Transaction(sc_node,
                                                    fromAddress=format_eoa(self.evm_address),
                                                    toAddress=native_contract_address,
                                                    value=eoa_nsc_amount
                                                    )
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # get mempool contents and check tx has been forged even if the fork is not active yet. Check the receipt
        response = allTransactions(sc_node, False)
        assert_true(tx_hash_eoa not in response['transactionIds'])
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_eoa))
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status)
        gas_used = int(receipt['result']['gasUsed'], 16)
        assert_equal(gas_used, 21000)

        # check the address has the expected balance
        nsc_bal = int(
            sc_node.rpc_eth_getBalance(format_evm(native_contract_address), 'latest')['result'], 16)
        assert_equal(nsc_bal, eoa_nsc_amount)

        # Deploy Smart Contract
        smart_contract_type = 'StorageTestContract'
        logging.info(f"Creating smart contract utilities for {smart_contract_type}")
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)
        initial_message = 'Initial message'
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node, initial_message,
                                                                fromAddress=self.evm_address,
                                                                gasLimit=10000000,
                                                                gasPrice=900000000)

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()
        method_get = 'get()'
        method_set = 'set(string)'

        # check we successfully deployed the smart contract, and we can get the initial string
        res = smart_contract.static_call(sc_node, method_get, fromAddress=self.evm_address,
                                         toAddress=smart_contract_address, gasPrice=900000000)
        assert_equal(initial_message, res[0])

        # Try a static call on proxy before reaching the fork point.
        sol_contract_call_data_get = smart_contract.raw_encode_call(method_get)
        tx_hash = ac_invokeProxy(
            sc_node,
            remove_0x_prefix(smart_contract_address),
            sol_contract_call_data_get,
            nonce=None,
            static=True)['result']['transactionId']
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # get mempool contents and check tx has been forged even if the fork is not active yet since it is processed
        # by the eoa msg processor. Check also the receipt and gas used greater than an eoa (due to contract code)
        response = allTransactions(sc_node, False)
        assert_true(tx_hash not in response['transactionIds'])
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_true(1, status)
        gas_used = int(receipt['result']['gasUsed'], 16)
        assert_true(gas_used > 21000)

        # reach the fork
        current_best_epoch = sc_node.block_forgingInfo()["result"]["bestBlockEpochNumber"]

        for i in range(0, INTEROPERABILITY_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

        # use static call proxy for getting string value from solidity smart contract
        # actually this is pretty useless since we are not getting back the result, we are just checking the call is OK
        # we will also test eth_call further on
        sol_contract_call_data_get = smart_contract.raw_encode_call(method_get)
        tx_hash_static_call_get = ac_invokeProxy(
            sc_node,
            remove_0x_prefix(smart_contract_address),
            sol_contract_call_data_get,
            nonce=None,
            static=True)['result']['transactionId']
        self.sc_sync_all()

        # get contract proxy call data from tx in mempool, we will use it later
        input_data_static = get_contract_input_data_from_mempool_tx(sc_node, tx_hash_static_call_get)

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # check receipt after forging and check it is successful (read only)
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_static_call_get))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)

        on_chain_nonce = int(sc_node.rpc_eth_getTransactionCount(format_evm(self.evm_address), 'latest')['result'], 16)

        req = {
            "from": format_evm(self.evm_address),
            "to": format_evm(PROXY_SMART_CONTRACT_ADDRESS),
            "nonce": on_chain_nonce,
            "gasLimit": 2300000,
            "gasPrice": 850000000,
            "value": 0,
            "data": add_0x_prefix(input_data_static)
        }
        response = sc_node.rpc_eth_call(req, 'latest')
        pprint.pprint(response)
        result = remove_0x_prefix(response['result'])
        start_data_offset = decode(['uint32'], hex_str_to_bytes(result[0:32 * 2]))[0]
        assert_equal(start_data_offset, 32)
        end_offset = start_data_offset + 32  # read 32 bytes, that is the string size
        str_size = decode(['uint32'], hex_str_to_bytes(result[start_data_offset * 2:end_offset * 2]))[0]
        assert_equal(initial_message, bytearray.fromhex(result[end_offset * 2:(end_offset + str_size) * 2]).decode())

        # change the contract storage via proxy native smart contract
        new_message_n1 = 'Proxy did it (n.1)'
        sol_contract_call_data_set_n1 = smart_contract.raw_encode_call(method_set, new_message_n1)

        # invoke the proxy native smart contract via static call, it should fail
        tx_hash_static_call_set = ac_invokeProxy(
            sc_node,
            remove_0x_prefix(smart_contract_address),
            sol_contract_call_data_set_n1,
            nonce=None,
            static=True)['result']['transactionId']
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # check receipt after forging and check it is failed (write protection)
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_static_call_set))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 0)

        # invoke the proxy native smart contract and modify data
        tx_hash = ac_invokeProxy(
            sc_node,
            remove_0x_prefix(smart_contract_address),
            sol_contract_call_data_set_n1,
            nonce=None)['result']['transactionId']
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)

        # read the new value in the solidity smart contract and check we successfully modified it
        res = smart_contract.static_call(sc_node, method_get, fromAddress=self.evm_address,
                                         toAddress=smart_contract_address, gasPrice=900000000)
        assert_equal(new_message_n1, res[0])

        # call the proxy via a hand made eth tx

        # change the contract storage via proxy native smart contract
        new_message_n2 = 'Proxy did it (n.2)'
        sol_contract_call_data_set_n2 = smart_contract.raw_encode_call(method_set, new_message_n2)

        method = 'invokeCall(address,bytes)'
        abi_str = function_signature_to_4byte_selector(method)
        encoded_abi_method_signature = encode_hex(abi_str)
        addr_padded_str = "000000000000000000000000" + remove_0x_prefix(smart_contract_address)
        data_input = encoded_abi_method_signature + addr_padded_str
        data_input += "0000000000000000000000000000000000000000000000000000000000000040"
        h_len = hex(len(remove_0x_prefix(sol_contract_call_data_set_n2)) // 2)
        data_input += "00000000000000000000000000000000000000000000000000000000000000" + remove_0x_prefix(HexStr(h_len))
        data_input += remove_0x_prefix(sol_contract_call_data_set_n2)
        data_input += "00000000000000000000000000000000000000000000000000000000"

        tx_hash = createEIP1559Transaction(
            sc_node, fromAddress=remove_0x_prefix(self.evm_address),
            toAddress=PROXY_SMART_CONTRACT_ADDRESS,
            gasLimit=10000000,
            maxPriorityFeePerGas=900000000,
            maxFeePerGas=900000000,
            value=0,
            data=data_input
        )

        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)

        # read the new value in the solidity smart contract and check we succesfully modified it
        res = smart_contract.static_call(sc_node, method_get, fromAddress=self.evm_address,
                                         toAddress=smart_contract_address, gasPrice=900000000)
        assert_equal(new_message_n2, res[0])

        # call the proxy via an eth tx (recursive)
        new_message_n3 = 'Proxy did it (n.3)'
        sol_contract_call_data_set_n3 = smart_contract.raw_encode_call(method_set, new_message_n3)
        '''
        9b679b4d
        00000000000000000000000000000000000000000000aaaaaaaaaaaaaaaaaaaa
        0000000000000000000000000000000000000000000000000000000000000040
        00000000000000000000000000000000000000000000000000000000000000e4
        9b679b4d000000000000000000000000840463d17b8c7833883eaa47d23b2646
        f7fd1fd900000000000000000000000000000000000000000000000000000000
        0000004000000000000000000000000000000000000000000000000000000000
        000000644ed3885e000000000000000000000000000000000000000000000000
        0000000000000020000000000000000000000000000000000000000000000000
        000000000000001250726f78792064696420697420286e2e3329000000000000
        0000000000000000000000000000000000000000000000000000000000000000
        0000000000000000000000000000000000000000000000000000000000000000
        '''
        method = 'invokeCall(address,bytes)'
        abi_str = function_signature_to_4byte_selector(method)
        encoded_abi_method_signature = encode_hex(abi_str)
        hex_len_sol_contract_data = hex(len(remove_0x_prefix(sol_contract_call_data_set_n3)) // 2)
        opcode_len = len(remove_0x_prefix(encoded_abi_method_signature)) // 2
        padding_len = 32 - opcode_len
        h_len = hex(4 + (3 * 32) + int(hex_len_sol_contract_data, 16) + padding_len)
        addr_padded_str1 = "000000000000000000000000" + PROXY_SMART_CONTRACT_ADDRESS
        addr_padded_str2 = "000000000000000000000000" + remove_0x_prefix(smart_contract_address)

        data_input = encoded_abi_method_signature
        data_input += addr_padded_str1
        data_input += "0000000000000000000000000000000000000000000000000000000000000040"
        data_input += "00000000000000000000000000000000000000000000000000000000000000" + remove_0x_prefix(HexStr(h_len))
        data_input += remove_0x_prefix(encoded_abi_method_signature)
        data_input += addr_padded_str2
        data_input += "0000000000000000000000000000000000000000000000000000000000000040"
        data_input += "00000000000000000000000000000000000000000000000000000000000000" + remove_0x_prefix(
            HexStr(hex_len_sol_contract_data))
        data_input += remove_0x_prefix(sol_contract_call_data_set_n3)
        data_input += "00000000000000000000000000000000000000000000000000000000"  # padding 1
        data_input += "00000000000000000000000000000000000000000000000000000000"  # padding 2

        tx_hash_rec = createEIP1559Transaction(
            sc_node, fromAddress=remove_0x_prefix(self.evm_address),
            toAddress=PROXY_SMART_CONTRACT_ADDRESS,
            gasLimit=10000000,
            maxPriorityFeePerGas=900000000,
            maxFeePerGas=900000000,
            value=0,
            data=data_input
        )

        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_rec))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)

        # read the new value in the solidity smart contract and check we successfully modified it
        res = smart_contract.static_call(sc_node, method_get, fromAddress=self.evm_address,
                                         toAddress=smart_contract_address, gasPrice=900000000)
        assert_equal(new_message_n3, res[0])

        # Verify tracing gives reasonable result for the call: native_contract->native_contract->EVM contract
        trace_response = sc_node.rpc_debug_traceCall(
            {
                "to": format_evm(PROXY_SMART_CONTRACT_ADDRESS),
                "nonce": 3,
                "input": data_input
            }, "latest", {
                "tracer": "callTracer"
            }
        )
        pprint.pprint(trace_response)
        assert_false("error" in trace_response)

        on_chain_nonce = int(sc_node.rpc_eth_getTransactionCount(format_evm(self.evm_address), 'latest')['result'], 16)

        # change the contract storage via proxy native smart contract which recursively calls itself a number of times
        rec_message = 'Proxy recursively did it'
        sol_contract_call_data_set_rec = smart_contract.raw_encode_call(method_set, rec_message)

        # invoke the proxy native smart contract
        tx_hash = ac_invokeProxy(
            sc_node,
            remove_0x_prefix(smart_contract_address),
            sol_contract_call_data_set_rec,
            nonce=on_chain_nonce + NUM_OF_RECURSIONS)['result']['transactionId']
        self.sc_sync_all()

        # call all but the last leaving a gap in the nonce sequences, in this way they are not forged
        for i in range(1, NUM_OF_RECURSIONS):
            # get the tx input data from mempool
            input_data = get_contract_input_data_from_mempool_tx(sc_node, tx_hash)

            # invoke the proxy native smart contract
            tx_hash = ac_invokeProxy(
                sc_node,
                PROXY_SMART_CONTRACT_ADDRESS,
                input_data,
                nonce=on_chain_nonce + i + NUM_OF_RECURSIONS
            )['result']['transactionId']
            self.sc_sync_all()

        # invoke the proxy native smart contract for the last time with the actual state nonce, so only this tx gets
        # forged while the others remain in the mempool because of the nonce gap we built
        input_data = get_contract_input_data_from_mempool_tx(sc_node, tx_hash)
        tx_hash = ac_invokeProxy(
            sc_node,
            PROXY_SMART_CONTRACT_ADDRESS,
            input_data,
            nonce=on_chain_nonce
        )['result']['transactionId']
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)

        # Verify tracing gives reasonable result for the call native contract->native_contract->EVM contract
        trace_response = sc_node.rpc_debug_traceCall(
            {
                "to": format_evm(PROXY_SMART_CONTRACT_ADDRESS),
                "nonce": 3,
                "input": add_0x_prefix(input_data)
            }, "latest", {
                "tracer": "callTracer"
            }
        )
        pprint.pprint(trace_response)
        lev_k = trace_response['result']
        gas_k = lev_k['gasUsed']
        for k in range(0, NUM_OF_RECURSIONS):
            # check we have no more frames than expected
            assert_equal(1, len(lev_k['calls']))
            lev_k = lev_k['calls'][0]
            # check each frame spends less gas than outer one
            assert_true(int(gas_k, 16) > int(lev_k['gasUsed'], 16))
            gas_k = lev_k['gasUsed']

        # check we have no more frames than expected
        assert_true('calls' not in lev_k)

        # read the new value in the solidity smart contract and check we successfully modified it
        res = smart_contract.static_call(sc_node, method_get, fromAddress=self.evm_address,
                                         toAddress=smart_contract_address, gasPrice=900000000)
        assert_equal(rec_message, res[0])

        # Get gas estimations
        estimation_interop = sc_node.rpc_eth_estimateGas(
            {
                "to": format_evm(PROXY_SMART_CONTRACT_ADDRESS),
                "nonce": 3,
                "input": add_0x_prefix(input_data)
            }
        )
        # check estimation has the same value of the outer frame in the previous trace
        assert_equal(estimation_interop['result'], trace_response['result']['gasUsed'])


if __name__ == "__main__":
    SCEvmProxyNsc().main()
