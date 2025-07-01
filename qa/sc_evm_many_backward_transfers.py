#!/usr/bin/env python3
import json
import logging
import time
import hashlib
from eth_hash.auto import keccak
import base58
from eth_abi import decode
from eth_utils import add_0x_prefix, encode_hex, event_signature_to_log_topic, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import generate_block_and_get_tx_receipt
from SidechainTestFramework.account.httpCalls.transaction.allWithdrawRequests import all_withdrawal_requests
from SidechainTestFramework.account.httpCalls.transaction.withdrawCoins import withdrawcoins
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import (computeForgedTxFee,
                                                  convertZenToZennies, convertZenniesToWei)
from SidechainTestFramework.sc_forging_util import check_mcreference_presence, check_mcreferencedata_presence
from SidechainTestFramework.scutil import (
    generate_next_block, generate_next_blocks
)
from test_framework.util import (
    assert_equal, assert_false, hex_str_to_bytes, fail
)

"""
Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

Note:
    This test can be executed in two modes:
    1. using no key rotation circuit (by default)
    2. using key rotation circuit (with --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation)
    With key rotation circuit can be executed in two modes:
    1. ceasing (by default)
    2. non-ceasing (with --nonceasing flag)

Test:
    For the SC node:
        - Sends Backward Transfers and confirms the number of transfers returned by the getBackwardTransfers RPC request.  
"""

def get_function_selector(function_signature):
    return keccak(function_signature.encode('utf-8'))[:4].hex()

def encode_uint32_argument(epoch):
    return epoch.to_bytes(32, byteorder='big')

def create_backward_transfer_request(epoch):
    function_signature = "getBackwardTransfers(uint32)"
    function_selector = get_function_selector(function_signature)

    epoch_value = epoch
    encoded_epoch = encode_uint32_argument(epoch_value)

    data_field = "0x" + function_selector + encoded_epoch.hex()

    return {
            "to": "0x0000000000000000000011111111111111111111",
            "data": data_field
        }

def get_backward_transfers(sc_node, request):
    try:
        response = sc_node.rpc_eth_call(request, "latest")
        if 'result' in response:
            response_bytes = bytes.fromhex(response['result'][2:])
            if int(response['result'], 16) == 0: # Check if the response is empty
                logging.info("No Backward Transfers found")
            else:
                if len(response_bytes) > 32: # Check if there is actual data
                    decoded_response = decode(['(bytes20,uint256)[]'], response_bytes)
                    logging.info(f"Successfully retrieved {len(decoded_response[0])} Backward Transfers")
                    return decoded_response
        elif 'error' in response:
            raise Exception(f"Error in rpc_eth_call: {response['error']['message']}")
        else:
            raise Exception("rpc_eth_call no result in response")
    except Exception as e:
        raise Exception("There was a problem fetching getBackwardTransfers: " + str(e))

class SCEvmBackwardTransfer(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=10, max_nonce_gap=1000)

    def run_test(self):
        time.sleep(0.1)

        number_of_bt = 3999
        max_transactions_per_block=16
        bt_amount_in_zen_1 = 0.00001
        sc_bt_amount_in_zennies_1 = convertZenToZennies(bt_amount_in_zen_1)

        ft_amount_in_zen = 10
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        hex_evm_addr = remove_0x_prefix(self.evm_address)

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        mc_node.generate(8)[7]
        generate_next_block(sc_node, "first node")

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Wait until Certificate appears in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Generate MC and SC block
        generate_next_blocks(sc_node, "first node", 1)[0]
        mc_node.generate(1)[0]
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Initialize the nonce count for the wallet
        wallet_nonce = 0

        # Keep track of the total number of backward transfers created
        bt_created = 0

        current_epoch_number=1
        mc_address1 = mc_node.getnewaddress()


        while bt_created < number_of_bt:

            # Create a backward transfer
            res = withdrawcoins(sc_node, mc_address1, sc_bt_amount_in_zennies_1,wallet_nonce)
            
            if "result" not in res:
                fail(f"Backward Transfer Failed - Nonce : {wallet_nonce} : " + json.dumps(res))
            
            bt_created += 1
            wallet_nonce += 1

            # Check if we've reached the maximum number of transactions for this block
            if bt_created % max_transactions_per_block == 0 and bt_created != 0:
                self.sc_sync_all()  
                logging.info(f"Backward Transfer {bt_created} Created.")

                # get mempool contents, we must have max_transactions_per_block forger stake txes
                mempoolList = sc_node.transaction_allTransactions(json.dumps({"format": False}))['result']['transactionIds']
                assert_equal(max_transactions_per_block, len(mempoolList))

                # Generate SC block
                generate_next_block(sc_node, "first node")
                self.sc_sync_all()

                try:
                    request = create_backward_transfer_request(1)
                    response = get_backward_transfers(sc_node, request)
                    logging.info(f"getBackwardTransfers successfully returned {str(len(response[0]))} BTs")
                    assert_equal(bt_created, len(response[0]))
                except Exception as e:
                    raise Exception("There was a problem fetching getBackwardTransfers: " + str(e))

if __name__ == "__main__":
    SCEvmBackwardTransfer().main()
