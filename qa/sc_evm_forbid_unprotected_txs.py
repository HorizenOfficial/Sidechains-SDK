#!/usr/bin/env python3
import time

from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import eoa_transaction
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, \
    SCNetworkConfiguration, SCCreationInfo
from SidechainTestFramework.scutil import generate_next_block, generate_account_proposition, \
    assert_true, assert_equal, bootstrap_sidechain_nodes, AccountModel, connect_sc_nodes
from httpCalls.block.best import http_block_best
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import websocket_port_by_mc_node_index, forward_transfer_to_sidechain, fail

"""
Check the EVM SC allowUnprotectedTxs property behaviour.

Configuration: bootstrap 2 SC nodes and start first with allowUnprotectedTxs set to false.
    - Create 2 SC nodes
    - Node 1 has allowUnprotectedTxs = false
    - Node 2 has allowUnprotectedTxs = true

Test:
    - Add legacy transaction
    - Verify that transaction is rejected
    - Add legacy transaction using eth API
    - Verify that transaction is rejected
    - Tx coming from another node (or forced into a block) should be allowed

"""


class SCEvmForbidUnprotectedTxs(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=10, forward_amount=10)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = [
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key=self.API_KEY,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled,
                allow_unprotected_txs=False),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key=self.API_KEY,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled,
                allow_unprotected_txs=True)
        ]

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, self.forward_amount, self.withdrawalEpochLength),
                                         *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=self.block_timestamp_rewind,
                                                                 model=AccountModel)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        connect_sc_nodes(sc_node_1, 1)
        self.evm_address = '0x' + sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_hex_address = remove_0x_prefix(self.evm_address)
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        recipient_keys = generate_account_proposition("seed3", 1)[0]
        recipient_proposition = recipient_keys.proposition

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      evm_hex_address,
                                      50,
                                      mc_return_address=mc_node.getnewaddress())
        self.block_id = generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      50,
                                      mc_return_address=mc_node.getnewaddress())
        self.block_id = generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        generate_next_block(sc_node_1, "first node")

        # tx1: http api
        try:
            createLegacyTransaction(sc_node_1,
                                    fromAddress=evm_hex_address,
                                    toAddress=recipient_proposition,
                                    value=1)
            fail("Legacy http transaction should fail")
        except RuntimeError as e:
            assert_true("Legacy unprotected transactions are not allowed" in e.args[0], "Got" + e.args[0])

        assert_equal(0, len(allTransactions(sc_node_1, False)['transactionIds']), "Mempool should be empty")

        # tx2: rpc api
        try:
            eoa_transaction(sc_node_1, from_addr=evm_hex_address, to_addr=recipient_proposition,
                            value=1)
            fail("Legacy rpc transaction should fail")
        except RuntimeError as e:
            assert_true("Legacy unprotected transactions are not allowed" in e.args[0], "Got" + e.args[0])

        assert_equal(0, len(allTransactions(sc_node_1, False)['transactionIds']), "Mempool should be empty")

        # tx3: forced tx
        tx_bytes = createLegacyTransaction(sc_node_1,
                                           fromAddress=evm_hex_address,
                                           toAddress=recipient_proposition,
                                           value=1,
                                           output_raw_bytes=True)

        forced_tx = signTransaction(sc_node_1, fromAddress=evm_hex_address, payload=tx_bytes)
        block_id = generate_next_block(sc_node_1, "first node", forced_tx=[forced_tx])
        block_data = sc_node_1.block_findById(blockId=block_id)
        assert_equal(len(block_data['result']['block']['sidechainTransactions']), 0)
        assert_equal(block_data['result']['block']['sidechainTransactions'][0]['legacy'], True)

        self.sc_sync_all()
        time.sleep(2)
        block_json = http_block_best(sc_node_2)
        assert_true(len(block_json["sidechainTransactions"]) == 1)

        # tx4: create legacy transaction on node 2 and sync it to node 1
        response = allTransactions(sc_node_1, False)
        assert_equal(0, len(response['transactionIds']))
        self.sync_all()
        self.sc_sync_all()
        createLegacyTransaction(sc_node_2,
                                fromAddress=evm_address_sc_node_2,
                                toAddress=recipient_proposition,
                                value=1)
        self.sc_sync_all()
        response = allTransactions(sc_node_1, False)
        assert_equal(1, len(response['transactionIds']))


if __name__ == "__main__":
    SCEvmForbidUnprotectedTxs().main()
