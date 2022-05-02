#!/usr/bin/env python3
import json

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, start_nodes, websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block

"""
Check that both MC and SC calculates ScTxCumCommTreeHash in the same way.

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the MC node.

Test:
    - Check SC genesis block ScTxCumCommTreeHash compatibility with the one in MC
    - Generate 1 MC block and 1 SC block. Check ScTxCumCommTreeHash compatibility.
    - Generate 3 MC blocks and 1 SC block with 3 MCBlockHeaders. Check ScTxCumCommTreeHash compatibility.
    - Generate 2 SC blocks without MC blocks. Then 1 MC block and 1 SC block with 3 MCBlockHeaders. Do the check.
    - Generate 10 MC blocks and 1 SC block with 10 MCBlockHeaders. Do the check.
"""
class SCCumCommTreeHash(SidechainTestFramework):

    sc_nodes_bootstrap_info=None

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        sc_node = self.sc_nodes[0]
        mc_node = self.nodes[0]

        # Test SC genesis block ScTxCumCommTreeHash compatibility with the one in MC
        mc_block_hash = mc_node.getbestblockhash()
        mc_block_json = mc_node.getblock(mc_block_hash)
        print("Genesis SC block MC reference scTxsCommitment = {0}, scCumTreeHash = {1}\n".format(
            mc_block_json["scTxsCommitment"], mc_block_json["scCumTreeHash"]))

        sc_mc_header_info = sc_node.mainchain_mainchainHeaderInfoByHash(json.dumps({"hash": mc_block_hash}))
        print(json.dumps(sc_mc_header_info, indent=4, sort_keys=True))

        assert_equal(mc_block_json["scCumTreeHash"],
                     sc_mc_header_info["result"]["mainchainHeaderInfo"]["cumulativeCommTreeHash"],
                     "Genesis block mcref scCumTreeHash is different to the MC one")

        # Test SC block with 1 MC header ScTxCumCommTreeHash compatibility
        mc_block_hash = mc_node.generate(1)[0]
        mc_block_json = mc_node.getblock(mc_block_hash)
        print("Second SC block MC reference scTxsCommitment = {0}, scCumTreeHash = {1}\n".format(
            mc_block_json["scTxsCommitment"], mc_block_json["scCumTreeHash"]))

        generate_next_block(sc_node, "scnode")
        sc_mc_header_info = sc_node.mainchain_mainchainHeaderInfoByHash(json.dumps({"hash": mc_block_hash}))
        print(json.dumps(sc_mc_header_info, indent=4, sort_keys=True))
        assert_equal(mc_block_json["scCumTreeHash"],
                     sc_mc_header_info["result"]["mainchainHeaderInfo"]["cumulativeCommTreeHash"],
                     "SC block mcref scCumTreeHash is different to the MC one")

        # Test SC block with 3 MC headers ScTxCumCommTreeHash compatibility
        mc_blocks_hashes = mc_node.generate(3)
        generate_next_block(sc_node, "scnode")

        for mc_block_hash in mc_blocks_hashes:
            mc_block_json = mc_node.getblock(mc_block_hash)
            sc_mc_header_info = sc_node.mainchain_mainchainHeaderInfoByHash(json.dumps({"hash": mc_block_hash}))
            assert_equal(mc_block_json["scCumTreeHash"],
                         sc_mc_header_info["result"]["mainchainHeaderInfo"]["cumulativeCommTreeHash"],
                         "SC block mcref scCumTreeHash is different to the MC one")

        # Test SC block with 1 MC headers and with 2 parent blocks without MC data.
        # Generate 2 SC blocks with no MC data.
        generate_next_blocks(sc_node, "scnode", 2)
        mc_block_hash = mc_node.generate(1)[0]
        mc_block_json = mc_node.getblock(mc_block_hash)

        generate_next_block(sc_node, "scnode")
        sc_mc_header_info = sc_node.mainchain_mainchainHeaderInfoByHash(json.dumps({"hash": mc_block_hash}))
        assert_equal(mc_block_json["scCumTreeHash"],
                     sc_mc_header_info["result"]["mainchainHeaderInfo"]["cumulativeCommTreeHash"],
                     "SC block mcref scCumTreeHash is different to the MC one")

        # Test SC block with 10 MC headers ScTxCumCommTreeHash compatibility
        mc_blocks_hashes = mc_node.generate(10)
        generate_next_block(sc_node, "scnode")

        for mc_block_hash in mc_blocks_hashes:
            mc_block_json = mc_node.getblock(mc_block_hash)
            sc_mc_header_info = sc_node.mainchain_mainchainHeaderInfoByHash(json.dumps({"hash": mc_block_hash}))
            assert_equal(mc_block_json["scCumTreeHash"],
                         sc_mc_header_info["result"]["mainchainHeaderInfo"]["cumulativeCommTreeHash"],
                         "SC block mcref scCumTreeHash is different to the MC one")



if __name__ == "__main__":
    SCCumCommTreeHash().main()
