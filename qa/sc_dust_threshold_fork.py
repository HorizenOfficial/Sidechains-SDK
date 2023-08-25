#!/usr/bin/env python3
import json
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_block, check_wallet_coins_balance, connect_sc_nodes, disconnect_sc_nodes_bi
from httpCalls.block.forgingInfo import http_block_forging_info
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from test_framework.util import assert_equal, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_true

"""
Check that after the hard fork, FT with amount < 54 satoshi is no longer possible
Configuration:
    Start 1 MC node and 2 SC node.
    SC node 1 connected to the MC node 1.
    SC node 2 connected to the MC node 1.
Test:
    For the SC node:
        - Before the fork, do a transaction sc1 -> sc2 with 50 satoshi, it should not fail.
        - Disconnect sc nodes, prepare transactions in each node mempools with amount > 50
        - Forge a new block that activates the fork
        - assert that mempool transactions are not applied and rejected
        - Assert that a transaction with 50 satoshi fails
        - Assert that a transaction with 100 satoshi works
"""


class SCDustThresholdFork(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                                                        '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length),
                                         sc_node1_configuration, sc_node2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return start_sc_nodes(2, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        sc_address_1 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc_address_2 = sc_node2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()
        _50cent = 50

        # Send some currency to sc_1
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      self.sc_nodes_bootstrap_info.genesis_account.publicKey,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress())
        mc_node.generate(1)
        generate_next_block(sc_node, "first node")

        # check we are still in pre-fork epoch
        forging_info = http_block_forging_info(sc_node)
        assert_equal(forging_info["bestBlockEpochNumber"], 2)

        # Send 50 satoshi to node2, it should be successful
        sendCoinsToAddress(sc_node, sc_address_2, _50cent, fee=0)
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()
        check_wallet_coins_balance(sc_node2, 0.00000050)

        # Create 2 more tx in mempools of sc1 and sc2 with value < 54 satoshi
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        sendCoinsToAddress(sc_node, sc_address_2, 37, fee=0)
        sendCoinsToAddress(sc_node2, sc_address_1, 44, fee=0)

        # switch to the next consensus epoch
        new_block_id = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        forging_info = http_block_forging_info(sc_node)
        assert_equal(3, forging_info["bestBlockEpochNumber"])

        # check that new epoch block does not contain invalid transactions
        new_block = sc_node.block_findById(blockId=new_block_id)
        assert_equal(0, len(new_block['result']['block']['sidechainTransactions']))

        # assert that mempool transactions were rejected
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()
        assert_equal(0, len(sc_node.transaction_allTransactions()['result']['transactions']))
        assert_equal(0, len(sc_node2.transaction_allTransactions()['result']['transactions']))
        check_wallet_coins_balance(sc_node2, 0.00000050)
        check_wallet_coins_balance(sc_node, 199.99999950)

        # Send 50 satoshi to node2, it should fail
        j = {"outputs": [{
            "publicKey": str(sc_address_2),
            "value": _50cent
        }], "fee": 50
        }
        request = json.dumps(j)
        response = sc_node.transaction_sendCoinsToAddress(request)
        assert_true('Coin box value [50] is below the threshold[54].' in response["error"]["detail"])

        # send 100, it should work
        sendCoinsToAddress(sc_node, sc_address_2, 100, fee=0)
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()
        check_wallet_coins_balance(sc_node2, 0.00000150)
        check_wallet_coins_balance(sc_node, 199.99999850)


if __name__ == "__main__":
    SCDustThresholdFork().main()
