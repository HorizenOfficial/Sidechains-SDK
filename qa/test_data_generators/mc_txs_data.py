#!/usr/bin/env python3
import json
import os
import time
from decimal import Decimal

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import start_nodes
from SidechainTestFramework.scutil import create_sidechain, cert_proof_keys_paths, \
    generate_random_field_element_hex, csw_proof_keys_paths
from SidechainTestFramework.sc_boostrap_info import SCCreationInfo, LARGE_WITHDRAWAL_EPOCH_LENGTH

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
        withdrawal_epoch_length = LARGE_WITHDRAWAL_EPOCH_LENGTH
        btr_data_length = 2

        sc_creation_info = SCCreationInfo(mc_node, creation_amount, withdrawal_epoch_length, btr_data_length)
        ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
        if not os.path.isdir(ps_keys_dir):
            os.makedirs(ps_keys_dir)
        boot_info = create_sidechain(sc_creation_info, 0, cert_proof_keys_paths(ps_keys_dir),
                                     csw_proof_keys_paths(ps_keys_dir, withdrawal_epoch_length))

        sidechain_id = boot_info.sidechain_id
        sc_creation_tx_id = mc_node.getblock(mc_node.getbestblockhash())["tx"][-1]
        sc_creation_tx_hex = mc_node.getrawtransaction(sc_creation_tx_id)

        print("MC Transaction with version -4 with single SidechainCreation output: \nHash = {0}\nSize = {1}\nHex = {2}"
              "\nsidechain_id = {3}\ncreation_amount = {4}, withdrawal_epoch_length = {5}\n"
              .format(str(sc_creation_tx_id), len(sc_creation_tx_hex) / 2, str(sc_creation_tx_hex),
                      sidechain_id, creation_amount, withdrawal_epoch_length))


        # Generate Tx with version -4 with single ForwardTransfer output
        forward_transfer_amount = 10
        mc_return_address = mc_node.getnewaddress()
        ft_args = [{
            "toaddress": boot_info.genesis_account.publicKey,
            "amount": forward_transfer_amount,
            "scid": sidechain_id,
            "mcReturnAddress": mc_return_address
        }]
        ft_tx_id = mc_node.sc_send(ft_args)
        ft_tx_hex = mc_node.getrawtransaction(ft_tx_id)
        print("MC Transaction with version -4 with single ForwardTransfer output: \nHash = {0}\nSize = {1}\nHex = {2}"
              "\nsidechain_id = {3}\nforward_transfer_amount = {4}, public_key = {5}\n"
              .format(str(ft_tx_id), len(ft_tx_hex) / 2, str(ft_tx_hex),
                      sidechain_id, forward_transfer_amount, boot_info.genesis_account.publicKey))

        # Sleep for 1 second to prevent MC RPC failure because of double spend (lack of synchronization).
        time.sleep(1)

        # Generate Tx with version -4 with multiple ForwardTransfer outputs
        send_many_params = [
            {
                "scid": sidechain_id,
                "amount": 10,
                "toaddress": "000000000000000000000000000000000000000000000000000000000000add1",
                "mcReturnAddress": mc_return_address
            },
            {
                "scid": sidechain_id,
                "amount": 11,
                "toaddress": "000000000000000000000000000000000000000000000000000000000000add2",
                "mcReturnAddress": mc_return_address
            },
            {
                "scid": sidechain_id,
                "amount": 12,
                "toaddress": "000000000000000000000000000000000000000000000000000000000000add3",
                "mcReturnAddress": mc_return_address
            }
        ]
        multiple_ft_tx_id = mc_node.sc_send(send_many_params)
        multiple_ft_tx_hex = mc_node.getrawtransaction(multiple_ft_tx_id)
        print("MC Transaction with version -4 with multiple ForwardTransfer outputs: \nHash = {0}\nSize = {1}\n"
              "Hex = {2}\nForward Transfers: = {3}\n"
              .format(str(multiple_ft_tx_id), len(multiple_ft_tx_hex) / 2, str(multiple_ft_tx_hex),
                      json.dumps(send_many_params, indent=4)))


        # Generate Tx with version -4 with single MBTR output
        fe1 = generate_random_field_element_hex()
        fe2 = generate_random_field_element_hex()
        pk1 = mc_node.getnewaddress()
        mbtrFee = 10
        mbtrOuts = [{'vScRequestData': [fe1, fe2], 'scFee': str(Decimal(mbtrFee)), 'scid': sidechain_id, 'mcDestinationAddress': pk1}]

        raw_tx = mc_node.createrawtransaction([], {}, [], [], [], mbtrOuts)
        funded_tx = mc_node.fundrawtransaction(raw_tx)
        signed_tx = mc_node.signrawtransaction(funded_tx['hex'])
        mbtr_tx_id = mc_node.sendrawtransaction(signed_tx['hex'])

        mbtr_tx_hex = mc_node.getrawtransaction(mbtr_tx_id)
        print("MC Transaction with version -4 with single MBTR output: \nHash = {0}\nSize = {1}\n"
              "Hex = {2}\nMBTR: = {3}\n"
              .format(str(mbtr_tx_id), len(mbtr_tx_hex) / 2, str(mbtr_tx_hex), json.dumps(mbtrOuts, indent=4)))


if __name__ == "__main__":
    McTxsData().main()
