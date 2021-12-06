#!/usr/bin/env python2
import json
import os
from decimal import Decimal

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import create_sidechain, \
    check_mainchain_block_reference_info, check_wallet_coins_balance, generate_next_blocks, cert_proof_keys_paths, \
    generate_random_field_element_hex, csw_proof_keys_paths
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


        ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
        if not os.path.isdir(ps_keys_dir):
            os.makedirs(ps_keys_dir)


        # Generate MC block with single sidechain mentioned - sidechain creation output
        sc_creation_info = SCCreationInfo(mc_node, 100, 1000, btr_data_length=2)
        boot_info = create_sidechain(sc_creation_info, 0, cert_proof_keys_paths(ps_keys_dir),
                                     csw_proof_keys_paths(ps_keys_dir, sc_creation_info.withdrawal_epoch_length))
        sidechain_id_1 = str(boot_info.sidechain_id)

        block_id = mc_node.getbestblockhash()
        block_hex = mc_node.getblock(block_id, False)
        block_json = mc_node.getblock(block_id)
        print("MC Block with Tx with single ScCreation output: \nHash = {0}\nHex = {1}\nJson = {2}\nScId = {3}\n\n"
              .format(str(block_id), str(block_hex), str(block_json), sidechain_id_1))


        # Declare 2 more sidechains
        boot_info = create_sidechain(sc_creation_info, 0, cert_proof_keys_paths(ps_keys_dir),
                                     csw_proof_keys_paths(ps_keys_dir, sc_creation_info.withdrawal_epoch_length))
        sidechain_id_2 = str(boot_info.sidechain_id)

        boot_info = create_sidechain(sc_creation_info, 0, cert_proof_keys_paths(ps_keys_dir),
                                     csw_proof_keys_paths(ps_keys_dir, sc_creation_info.withdrawal_epoch_length))
        sidechain_id_3 = str(boot_info.sidechain_id)


        # Generate MC block with 1 FT.
        sc_address = "000000000000000000000000000000000000000000000000000000000000add1"
        mc_return_address = mc_node.getnewaddress()
        ft_args = [{
            "toaddress": sc_address,
            "amount": 10,  # 10 Zen
            "scid": sidechain_id_1,
            "mcReturnAddress": mc_return_address
        }]
        mc_node.sc_send(ft_args)
        # Generate block
        block_id = mc_node.generate(1)[0]
        block_hex = mc_node.getblock(block_id, False)
        block_json = mc_node.getblock(block_id)
        print("MC Block with with Tx single FT: \nHash = {0}\nHex = {1}\nJson = {2}\nScId = {3}\n\n"
            .format(str(block_id), str(block_hex), str(block_json), sidechain_id_1))


        # Generate MC block with 3 sidechains mentioned.
        sc_address = "000000000000000000000000000000000000000000000000000000000000add1"
        # Send 3 FTs to different sidechains
        ft_args = [
            {"toaddress": sc_address, "amount": 1, "scid": sidechain_id_1, "mcReturnAddress": mc_return_address},
            {"toaddress": sc_address, "amount": 2, "scid": sidechain_id_2, "mcReturnAddress": mc_return_address},
            {"toaddress": sc_address, "amount": 3, "scid": sidechain_id_3, "mcReturnAddress": mc_return_address}
        ]

        mc_node.sc_send(ft_args)

        # Generate block
        block_id = mc_node.generate(1)[0]
        block_hex = mc_node.getblock(block_id, False)
        block_json = mc_node.getblock(block_id)
        # Note: we sort only 2 last characters, so almost equal to the sort of the little-endian bytes
        sidechain_ids = [sidechain_id_1, sidechain_id_2, sidechain_id_3]
        sorted_sidechain_ids = sorted(sidechain_ids, key = lambda x: x[-2:])
        print("MC Block with multiple SCs mentioned (3 FT for different sidechains): \nHash = {0}\nHex = {1}\nJson = {2}\nSidechains = {3}\n"
              .format(str(block_id), str(block_hex), str(block_json), sorted_sidechain_ids))


        # Generate block with 1 MBTR
        fe1 = generate_random_field_element_hex()
        fe2 = generate_random_field_element_hex()
        pk1 = mc_node.getnewaddress()
        mbtrFee = 10
        mbtrOuts = [
            {'vScRequestData': [fe1, fe2], 'scFee': str(Decimal(mbtrFee)), 'scid': sidechain_id_1, 'mcDestinationAddress': pk1}]
        # Generate Tx with version -4 with single MBTR output
        raw_tx = mc_node.createrawtransaction([], {}, [], [], [], mbtrOuts)
        funded_tx = mc_node.fundrawtransaction(raw_tx)
        signed_tx = mc_node.signrawtransaction(funded_tx['hex'])
        mbtr_tx_id = mc_node.sendrawtransaction(signed_tx['hex'])
        # Generate block
        block_id = mc_node.generate(1)[0]
        block_hex = mc_node.getblock(block_id, False)
        block_json = mc_node.getblock(block_id)
        print("MC Block with Tx with single MBTR output: \nHash = {0}\nHex = {1}\nJson = {2}\nScId = {3}\n\n"
              .format(str(block_id), str(block_hex), str(block_json), sidechain_id_1))


if __name__ == "__main__":
    McTxsData().main()
