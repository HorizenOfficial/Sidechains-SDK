#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.utils import convertZenToZennies
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block, generate_account_proposition
from test_framework.util import assert_equal, assert_true

"""
Check the EVM SC block header values gasUsed and baseFee.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Verify that initial base fee decreases by 12.5 % on empty blocks
    - Add two eoa to eoa transactions
    - Verify that gasUsed is correct
    - Verify that base fee decrease is correct
"""


class SCEvmBaseFee(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=10)

    def run_test(self):
        self.sc_ac_setup()

        sc_node = self.sc_nodes[0]
        evm_hex_address = remove_0x_prefix(self.evm_address)

        sc_best_block = sc_node.block_best()["result"]
        assert_equal(875000000, sc_best_block['block']['header']['baseFee'])

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)

        sc_best_block = sc_node.block_best()["result"]
        assert_equal(765625000, sc_best_block['block']['header']['baseFee'])

        # Create an EOA to EOA transaction moving some fund to a new address not known by wallet.
        # Amount should be expressed in zennies
        transferred_amount = Decimal(2)
        transferred_amount_in_zennies = convertZenToZennies(transferred_amount)

        recipient_keys = generate_account_proposition("seed3", 1)[0]
        recipient_proposition = recipient_keys.proposition
        logging.info("Trying to send {} zen to address {}".format(transferred_amount, recipient_proposition))

        j = {
            "from": evm_hex_address,
            "to": recipient_proposition,
            "value": transferred_amount_in_zennies,
            "nonce": 0
        }
        request = json.dumps(j)
        response = sc_node.transaction_sendCoinsToAddress(request)
        assert_true('transactionId' in response['result'], "Transaction failed")

        # send more zen to have more than one transaction in block
        recipient_keys = generate_account_proposition("seed4", 1)[0]
        recipient_proposition = recipient_keys.proposition
        logging.info("Trying to send {} zen to address {}".format(transferred_amount, recipient_proposition))

        j = {
            "from": evm_hex_address,
            "to": recipient_proposition,
            "value": transferred_amount_in_zennies,
            "nonce": 1
        }
        request = json.dumps(j)
        response = sc_node.transaction_sendCoinsToAddress(request)
        assert_true('transactionId' in response['result'], "Transaction failed")

        generate_next_blocks(sc_node, "first node", 1)

        sc_best_block = sc_node.block_best()["result"]
        # check if header contains correct gasUsed (2 * eoa to eoa transfer gas costs)
        assert_equal(21000 * 2, sc_best_block['block']['header']['gasUsed'])

        assert_equal(669921875, sc_best_block['block']['header']['baseFee'])


if __name__ == "__main__":
    SCEvmBaseFee().main()
