#!/usr/bin/env python2
import json
import os

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import create_sidechain, \
    check_mainchain_block_reference_info, check_wallet_coins_balance, generate_next_blocks, proof_keys_paths
from SidechainTestFramework.sc_boostrap_info import SCCreationInfo, Account

"""
Generate MC transactions for Unit tests

Configuration: 1 MC node

Test:
    - generate MC Tx with version -4 with no SC data
    - generate MC Tx with version -4 with single SidechainCreation output
    - generate MC Tx with version -4 with single ForwardTransfer output
    - generate MC Tx with version -4 with multiple ForwardTransfer outputs
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


        # Generate Tx with version -4 with no SC related outputs
        mc_address = mc_node.getnewaddress()
        tx_id = mc_node.sendtoaddress(mc_address, 10)
        tx_hex = mc_node.getrawtransaction(tx_id)

        print("MC Transaction with version -4 without SC data: \nHash = {0}\nSize = {1}\nHex = {2}\n"
              .format(str(tx_id), len(tx_hex)/2, str(tx_hex)))


        # Generate Tx with version -4 with single SidechainCreation output
        # Use the same amount and withdrawal epoch length as for unit test
        creation_amount = 50
        withdrawal_epoch_length = 1000

        sc_creation_info = SCCreationInfo(mc_node, creation_amount, withdrawal_epoch_length)
        ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
        if not os.path.isdir(ps_keys_dir):
            os.makedirs(ps_keys_dir)
        boot_info = create_sidechain(sc_creation_info, 0, proof_keys_paths(ps_keys_dir))

        sidechain_id = boot_info.sidechain_id
        sc_creation_tx_id = mc_node.getblock(mc_node.getbestblockhash())["tx"][-1]
        sc_creation_tx_hex = mc_node.getrawtransaction(sc_creation_tx_id)

        print("MC Transaction with version -4 with single SidechainCreation output: \nHash = {0}\nSize = {1}\nHex = {2}"
              "\nsidechain_id = {3}\ncreation_amount = {4}, withdrawal_epoch_length = {5}\n"
              .format(str(sc_creation_tx_id), len(sc_creation_tx_hex) / 2, str(sc_creation_tx_hex),
                      sidechain_id, creation_amount, withdrawal_epoch_length))


        # Generate Tx with version -4 with single ForwardTransfer output
        forward_transfer_amount = 10
        ft_tx_id = mc_node.sc_send(boot_info.genesis_account.publicKey, forward_transfer_amount, sidechain_id)
        ft_tx_hex = mc_node.getrawtransaction(ft_tx_id)
        print("MC Transaction with version -4 with single ForwardTransfer output: \nHash = {0}\nSize = {1}\nHex = {2}"
              "\nsidechain_id = {3}\nforward_transfer_amount = {4}, public_key = {5}\n"
              .format(str(ft_tx_id), len(ft_tx_hex) / 2, str(ft_tx_hex),
                      sidechain_id, forward_transfer_amount, boot_info.genesis_account.publicKey))


        # Generate Tx with version -4 with multiple ForwardTransfer outputs
        send_many_params = [
            {
                "scid": sidechain_id,
                "amount": 10,
                "address": "000000000000000000000000000000000000000000000000000000000000add1"
            },
            {
                "scid": sidechain_id,
                "amount": 11,
                "address": "000000000000000000000000000000000000000000000000000000000000add2"
            },
            {
                "scid": sidechain_id,
                "amount": 12,
                "address": "000000000000000000000000000000000000000000000000000000000000add3"
            }
        ]
        multiple_ft_tx_id = mc_node.sc_sendmany(send_many_params)
        multiple_ft_tx_hex = mc_node.getrawtransaction(multiple_ft_tx_id)
        print("MC Transaction with version -4 with multiple ForwardTransfer outputs: \nHash = {0}\nSize = {1}\n"
              "Hex = {2}\nForward Transfers: = {3}\n"
              .format(str(multiple_ft_tx_id), len(multiple_ft_tx_hex) / 2, str(multiple_ft_tx_hex),
                      json.dumps(send_many_params, indent=4)))


if __name__ == "__main__":
    McTxsData().main()
