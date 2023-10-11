import logging
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import get_withdrawal_epoch, bootstrap_sidechain_nodes, start_sc_nodes, \
    generate_next_block, \
    generate_next_blocks
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, assert_true, disconnect_nodes_bi

"""
Configuration:
    Start 2 MC nodes and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

Test:
    Connect the 2 mc nodes: first_mc_node is connected to sc_node, second_mc_node is only connected to the other mc node
    Mine 10 blocks (withdrawal epoch length)
    Forge a block to sync with mainchian headers, check we are at withdrawal epoch 1
    Wait for the certificate to be sent to mc mempool
    Mine a mc block
    Verify it's included in the next mc block
    Forge 9 blocks to reach the withdrawal epoch length
    Verify we're at epoch 2
    Disconnect first_mc_node from second_mc_node and vice versa
    Mine 30 blocks with second_mc_node to have a 3 withdrawal epochs gap
    Forge sc blocks
    Verify first_mc_node is left behind
    Verify sc_node is still at epoch 2
    Reconnect first_mc_node to second_mc_node
    Let them sync
    Verify they are at the same height
    Forge a sc block and verify it contains 30 mainchain block headers
    Verify sc is still at withdrawal epoch 2
    Mine another 10 mc blocks
    Forge sc blocks
    Verify sc update withdrawal epoch correctly: 2 (previous epoch) + 3 (epochs while first_mc_node was offline) + 1 (last epoch) = 6
    Wait for the 4 certificates to be created and sent to mc
    Increase once again the withdrawal epoch
    Wait for the final certificate
"""

class WithdrawalCertificateAfterMainchainNodesWereDisconnected(SidechainTestFramework):

    number_of_mc_nodes = 2
    number_of_sidechain_nodes = 1
    sc_nodes_bootstrap_info=None

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node_1, 600, 10, is_non_ceasing=True, circuit_type=KEY_ROTATION_CIRCUIT, sc_creation_version=2),
            sc_node_1_configuration
        )
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes

        connect_nodes_bi(mc_nodes, 0, 1)

        logging.info("Number of started mc nodes: {0}".format(len(mc_nodes), "The number of MC nodes is not {0}.".format(self.number_of_mc_nodes)))
        logging.info("Number of started sc nodes: {0}".format(len(sc_nodes), "The number of SC nodes is not {0}.".format(self.number_of_sidechain_nodes)))

        first_mainchain_node = mc_nodes[0]
        second_mainchain_node = mc_nodes[1]

        sc_node = sc_nodes[0]

        ## Check node is a submitter
        assert_true(sc_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"], "Node 1 submitter expected to be enabled.")

        epoch = get_withdrawal_epoch(sc_node)
        assert_equal(0, epoch)

        block_hash = first_mainchain_node.generate(9)[-1]
        self.sync_all()
        first_mainchain_node_new_block = first_mainchain_node.getblock(block_hash)
        second_mainchain_node_new_block = second_mainchain_node.getblock(block_hash)
        # Check mc nodes are synced
        assert_equal(first_mainchain_node_new_block, second_mainchain_node_new_block)

        generate_next_block(sc_node, "first node")
        sc_block = sc_node.block_best()["result"]["block"]
        # Check sc node is following the mc
        assert_equal(9, len(sc_block["mainchainHeaders"]))

        # Generate one more block to reach withdrawal epoch
        first_mainchain_node.generate(1)
        self.sync_all()

        generate_next_block(sc_node, "first node")

        epoch = get_withdrawal_epoch(sc_node)
        # Check withdrawal epoch switching
        assert_equal(1, epoch)

        # Wait until Certificate will appear in MC node mempool
        self.wait_for_cert_to_appear_in_mc(first_mainchain_node, sc_node)

        assert_equal(1, first_mainchain_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        sc_cert_hash = first_mainchain_node.getrawmempool()[0]

        # Generate 10 mc block and retrieve the certificate from the first one
        mc_block = first_mainchain_node.generate(10)[0]
        self.sync_all()
        certs = first_mainchain_node.getblock(mc_block)["cert"]
        assert_equal(1, len(certs))
        assert_equal(sc_cert_hash, certs[0])

        generate_next_blocks(sc_node, "first node", 5)

        epoch = get_withdrawal_epoch(sc_node)
        # Check withdrawal epoch switching
        assert_equal(2, epoch)

        # Disconnect first_mainchain_node, that is connected to sc_node, from second_mainchain_node
        disconnect_nodes_bi(self.nodes, 0, 1)

        # Generate 30 mc blocks to have a gap of 3 withdrawal epochs between first and second mc blocks
        for _ in range(3):
            second_mainchain_node.generate(9)
            generate_next_block(sc_node, "first node")

            second_mainchain_node.generate(1)
            generate_next_block(sc_node, "first node")

        first_mc_node_block_count = first_mainchain_node.getblockcount()
        second_mc_node_block_count = second_mainchain_node.getblockcount()

        # Check the second_mainchain_node's height is 30 blocks greater than first_mainchain_node's one
        assert_equal(30, second_mc_node_block_count - first_mc_node_block_count)

        # Check sc_node withdrawal epoch is still 2
        epoch = get_withdrawal_epoch(sc_node)
        assert_equal(2, epoch)

        # Check the mainchain headers before reconnecting the mc nodes
        generate_next_block(sc_node, "first node")
        sc_block = sc_node.block_best()["result"]["block"]
        # Check sc node is following the mc
        assert_equal(0, len(sc_block["mainchainHeaders"]))

        # Connect the mc nodes
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_nodes([first_mainchain_node, second_mainchain_node])

        first_mc_node_block_count = first_mainchain_node.getblockcount()
        second_mc_node_block_count = second_mainchain_node.getblockcount()

        # Check that both mc nodes are synced again
        assert_equal(first_mc_node_block_count, second_mc_node_block_count)

        # Check the sidechain has caught up with mc headers...
        generate_next_block(sc_node, "first node")
        sc_block = sc_node.block_best()["result"]["block"]
        assert_equal(30, len(sc_block["mainchainHeaders"]))

        # ... but still at withdrawal epoch 2
        epoch = get_withdrawal_epoch(sc_node)
        assert_equal(2, epoch)

        # Increase the withdrawal epoch
        mc_blocks = first_mainchain_node.generate(10)
        self.sync_all()

        for block in mc_blocks:
            assert_equal(0, len(first_mainchain_node.getblock(block)["cert"]))

        generate_next_blocks(sc_node, "first node", 5)
        # The epoch should be 6 since the sc has caught up with ma
        epoch = get_withdrawal_epoch(sc_node)
        assert_equal(6, epoch)

        for _ in range(epoch - 1):
            generate_next_blocks(sc_node, "first node", 5)

            # Wait until Certificate will appear in MC node mempool
            self.wait_for_cert_to_appear_in_mc(first_mainchain_node, sc_node)
            assert_equal(1, first_mainchain_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
            sc_cert_hash = first_mainchain_node.getrawmempool()[0]

            mc_block = first_mainchain_node.generate(1)[0]
            self.sync_all()
            certs = first_mainchain_node.getblock(mc_block)["cert"]
            assert_equal(1, len(certs))
            assert_equal(sc_cert_hash, certs[0])

        block_hash = first_mainchain_node.generate(9)[-1]
        self.sync_all()
        first_mainchain_node_new_block = first_mainchain_node.getblock(block_hash)
        second_mainchain_node_new_block = second_mainchain_node.getblock(block_hash)
        # Check mc nodes are synced
        assert_equal(first_mainchain_node_new_block, second_mainchain_node_new_block)

        generate_next_block(sc_node, "first node")
        sc_block = sc_node.block_best()["result"]["block"]
        # Check sc node is following the mc
        assert_equal(10, len(sc_block["mainchainHeaders"]))

        # Generate one more block to reach withdrawal epoch
        first_mainchain_node.generate(1)
        self.sync_all()

        generate_next_block(sc_node, "first node")

        epoch = get_withdrawal_epoch(sc_node)
        # Check withdrawal epoch switching
        assert_equal(7, epoch)

        # Wait until Certificate will appear in MC node mempool
        self.wait_for_cert_to_appear_in_mc(first_mainchain_node, sc_node)

        assert_equal(1, first_mainchain_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        sc_cert_hash = first_mainchain_node.getrawmempool()[0]

        # Generate 10 mc block and retrieve the certificate from the first one
        mc_block = first_mainchain_node.generate(1)[0]
        self.sync_all()
        certs = first_mainchain_node.getblock(mc_block)["cert"]
        assert_equal(1, len(certs))
        assert_equal(sc_cert_hash, certs[0])


    def wait_for_cert_to_appear_in_mc(self, first_mainchain_node, sc_node, max_check_times = 100):
        time.sleep(10)
        check_counter = 0
        while first_mainchain_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"] \
                and check_counter < max_check_times:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
            check_counter += 1

        if check_counter == max_check_times:
            raise Exception(f"Certificate did not appear in mc after {max_check_times} checks")

if __name__ == "__main__":
    WithdrawalCertificateAfterMainchainNodesWereDisconnected().main()