#!/usr/bin/env python3
import json
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_block
from httpCalls.block.forgingInfo import http_block_forging_info
from qa.httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
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
        - Switch to the next epoch, where the fork is enabled
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
        # After bug spotted in 0.3.4 we test certificate generation with max keys number > 8
        cert_max_keys = 10
        cert_sig_threshold = 6

        mc_node = self.nodes[0]
        sc_node1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            submitter_private_keys_indexes=list(range(cert_max_keys))  # SC node owns all schnorr private keys.
        )
        sc_node2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            submitter_private_keys_indexes=list(range(cert_max_keys))  # SC node owns all schnorr private keys.
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length,
                                                        cert_max_keys=cert_max_keys,
                                                        cert_sig_threshold=cert_sig_threshold),
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
        _50cent = 50

        # Send some currency to sc_1
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      self.sc_nodes_bootstrap_info.genesis_account.publicKey,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress())
        mc_node.generate(1)
        generate_next_block(sc_node, "first node")

        # check we are still in pre-fork epoch and cert has 0 ftScFee
        forging_info = http_block_forging_info(sc_node)
        assert_equal(forging_info["bestEpochNumber"], 2)

        # Send 50 satoshi to node2, it should be successful
        sc_address_2 = sc_node2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sendCoinsToAddress(sc_node, sc_address_2, _50cent, fee=0)

        # switch to the next consensus epoch, generate new cert, check that ftScFee is 54 now
        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        forging_info = http_block_forging_info(sc_node)
        assert_equal(forging_info["bestEpochNumber"], 3)

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


if __name__ == "__main__":
    SCDustThresholdFork().main()
