#!/usr/bin/env python2
import json
import os

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import create_sidechain, \
    check_mainchain_block_reference_info, check_wallet_coins_balance, generate_next_blocks, proof_keys_paths
from SidechainTestFramework.sc_boostrap_info import SCCreationInfo, Account

"""
Generate MC Blocks data for Unit tests

Configuration: 1 MC node

Test:
    - generate MC Block without sidechains
    - generate MC Block with 3 sidechains mentioned
"""


class McTxsData(SidechainTestFramework):
    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        pass

    def sc_setup_nodes(self):
        pass

    def sc_setup_network(self, split=False):
        pass

    def run_test(self):
        mc_node = self.nodes[0]
        mc_node.generate(200)


        # Generate MC Block without sidechains.
        block_id = mc_node.generate(1)[0]
        block_hex = mc_node.getblock(block_id, False)
        block_json = mc_node.getblock(block_id)

        print("MC Block without SC data: \nHash = {0}\nHex = {1}\nJson = {2}\n"
              .format(str(block_id), str(block_hex), str(block_json)))


        # Generate MC block with 3 sidechains mentioned.
        ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
        if not os.path.isdir(ps_keys_dir):
            os.makedirs(ps_keys_dir)

        sc_creation_info = SCCreationInfo(mc_node, 100, 1000)
        boot_info = create_sidechain(sc_creation_info, 0, proof_keys_paths(ps_keys_dir))
        sidechain_id_1 = str(boot_info.sidechain_id)

        boot_info = create_sidechain(sc_creation_info, 0, proof_keys_paths(ps_keys_dir))
        sidechain_id_2 = str(boot_info.sidechain_id)

        boot_info = create_sidechain(sc_creation_info, 0, proof_keys_paths(ps_keys_dir))
        sidechain_id_3 = str(boot_info.sidechain_id)

        sc_address = "000000000000000000000000000000000000000000000000000000000000add1"
        # Send 3 FTs to different sidechains
        mc_node.sc_send(sc_address, 1, sidechain_id_1) # 1 Zen
        mc_node.sc_send(sc_address, 2, sidechain_id_2) # 2 Zen
        mc_node.sc_send(sc_address, 3, sidechain_id_3) # 3 Zen
        # Generate block
        block_id = mc_node.generate(1)[0]
        block_hex = mc_node.getblock(block_id, False)
        block_json = mc_node.getblock(block_id)
        # Note: we sort only 2 last characters, so almost equal to the sort of the little-endian bytes
        sidechain_ids = [sidechain_id_1, sidechain_id_2, sidechain_id_3]
        sorted_sidechain_ids = sorted(sidechain_ids, key = lambda x: x[-2:])
        print("MC Block with multiple SCs mentioned: \nHash = {0}\nHex = {1}\nJson = {2}\nSidechains = {3}\n"
              .format(str(block_id), str(block_hex), str(block_json), sorted_sidechain_ids))


if __name__ == "__main__":
    McTxsData().main()
