#!/usr/bin/env python3

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_true, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes

"""
Test CSW API with CSW enabled.


Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    The SC has CSW enabled.
    SC node is forger, but not a certificate submitter.

Test:
    - isCSWEnabled method shall return true
    
Note: the other API methods are already tested with other tests, so they won't be tested here. 
"""


class CSWApiWithCSWEnabledTest(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    sc_creation_amount = 100  # Zen

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-logtimemicros=1']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False  # not a certificate submitter
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, self.sc_creation_amount, self.sc_withdrawal_epoch_length),
            sc_node_1_configuration)

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 5)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        sc_node = self.sc_nodes[0]

        print("Calling isCSWEnabled...")
        is_csw_enabled = sc_node.csw_isCSWEnabled()["result"]["cswEnabled"]

        assert_true(is_csw_enabled, "Ceased Sidechain Withdrawal expected to be enabled.")
        print("OK\n")


if __name__ == "__main__":
    CSWApiWithCSWEnabledTest().main()
