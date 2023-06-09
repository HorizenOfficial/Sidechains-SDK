#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.utils import convertZenToZennies
from SidechainTestFramework.scutil import generate_next_block, \
    assert_equal
from test_framework.util import forward_transfer_to_sidechain, fail

"""
Check eth_feeHistory pending block tag works properly

Configuration:
    - 2 SC nodes connected with each other
    - 1 MC node

Test:
    - Add some transactions to the SC nodes. 
    - Get node 1 and node 2 fee histories with pending block tag
    - Verify that they are equal and return results with valid lengths w.r.t. blocks mined and transactions
    - Get node 1 and node 2 fee histories with latest block tag
    - Verify that they are equal and return results with valid lengths w.r.t. blocks mined and transactions
"""


class SCEvmFeeHistory(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('500.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc2,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        self.sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        nonce_addr_1 = 0
        nonce_addr_2 = 0

        sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        forgerStake_amount = 300  # Zen

        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_1, evm_address_sc1, sc2_blockSignPubKey,
                                                    sc2_vrfPubKey,
                                                    convertZenToZennies(forgerStake_amount))
        nonce_addr_1 += 1
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()

        # Generate SC blocks on SC node to make node 2 a forger
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        common_tx_list = []
        for i in range(10):
            common_tx_list.append(
                createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                         nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            common_tx_list.append(
                createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc2, toAddress=evm_address_sc1,
                                         nonce=nonce_addr_2, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_1 += 1
            nonce_addr_2 += 1

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Create txs on node 1
        for i in range(3):
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                     nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                     maxFeePerGas=900000000, value=1)
            nonce_addr_1 += 1

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Create txs on node 2
        node_2_tx_list = []
        for i in range(5):
            node_2_tx_list.append(
                createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc2, toAddress=evm_address_sc1,
                                         nonce=nonce_addr_2, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_2 += 1

        generate_next_block(sc_node_2, "second node")
        self.sc_sync_all()

        sc_height = sc_node_1.block_best()["result"]['height']

        history_pending_sc_node_1 = sc_node_1.rpc_eth_feeHistory("0x10", "pending", [25.0, 75.0])
        history_pending_sc_node_2 = sc_node_2.rpc_eth_feeHistory("0x10", "pending", [25.0, 75.0])

        history_latest_sc_node_1 = sc_node_1.rpc_eth_feeHistory("0x10", "latest", [25.0, 75.0])
        history_latest_sc_node_2 = sc_node_2.rpc_eth_feeHistory("0x10", "latest", [25.0, 75.0])

        assert_equal(history_pending_sc_node_1['result'], history_pending_sc_node_2['result'], "fee history should be equal among connected nodes")
        assert_equal(history_latest_sc_node_1['result'], history_latest_sc_node_2['result'], "fee history should be equal among connected nodes")
        assert_equal(history_pending_sc_node_1['result'], history_latest_sc_node_1['result'], "fee history should be equal for pending and latest block tag")

        assert_equal('0x1', history_pending_sc_node_1['result']['oldestBlock'])
        assert_equal(sc_height + 1, len(history_pending_sc_node_1['result']['baseFeePerGas']))  # +1 because baseFeePerGas is calculated for the next block after the requested range
        assert_equal(sc_height, len(history_pending_sc_node_1['result']['gasUsedRatio']))
        assert_equal(sc_height, len(history_pending_sc_node_1['result']['reward']))


if __name__ == "__main__":
    SCEvmFeeHistory().main()
