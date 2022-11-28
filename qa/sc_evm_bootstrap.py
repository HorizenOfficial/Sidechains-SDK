#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import format_evm
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.scutil import is_mainchain_block_included_in_sc_block, \
    check_mainchain_block_reference_info, \
    generate_next_blocks, generate_next_block, generate_account_proposition, \
    convertZenniesToWei, convertZenToZennies, computeForgedTxFee
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.wallet.allPublicKeys import http_wallet_allPublicKeys
from httpCalls.wallet.createPrivateKeySecp256k1 import http_wallet_createPrivateKeySec256k1
from test_framework.util import assert_equal, assert_true, forward_transfer_to_sidechain

"""
Check the EVM bootstrap feature.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the SC node:
        - verify the MC block is included
"""


class SCEvmBootstrap(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def run_test(self):

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = self.nodes[0].getblock(mc_block["hash"], False)
        logging.info("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node_1.block_best()["result"]
        logging.info(sc_best_block)

        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node_1.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        mc_return_address = self.nodes[0].getnewaddress()
        # evm_address = generate_account_proposition("seed2", 1)[0]

        # test we can not use a wrong key
        exception_occurs = False
        try:
            http_wallet_createPrivateKeySec256k1(sc_node_1, "qqq")
        except Exception as e:
            exception_occurs = True
            logging.info("We had an exception as expected: {}".format(str(e)))
        finally:
            assert_true(exception_occurs, "Using a wrong key should fail")

        evm_address = http_wallet_createPrivateKeySec256k1(sc_node_1)
        evm_hex_address = format_evm(evm_address)
        logging.info("pubkey = {}".format(evm_address))

        evm_address_2 = http_wallet_createPrivateKeySec256k1(sc_node_1)
        logging.info("pubkey = {}".format(evm_address_2))

        # call a legacy wallet api
        ret = http_wallet_allPublicKeys(sc_node_1)
        logging.info(ret)

        ft_amount_in_zen = Decimal("33.22")
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      evm_address,
                                      ft_amount_in_zen,
                                      mc_return_address)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      evm_address_2,
                                      ft_amount_in_zen,
                                      mc_return_address)

        # input("\n\t======> Enter any input to continue generating a new sc block...")
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()
        sc_best_block = sc_node_1.block_best()["result"]
        assert_equal(2, sc_best_block["height"], "The best block has not the specified height.")
        assert_equal(875000000, sc_best_block['block']['header']['baseFee'])
        logging.info(sc_best_block)
        logging.info(sc_node_1.rpc_eth_getBalance(evm_hex_address, "1"))

        # Check account balance via rpc not using optional tag
        assert_equal(ft_amount_in_wei, int(sc_node_1.rpc_eth_getBalance(evm_hex_address)['result'], 16))

        # input("\n\t======> Enter any input to continue generating blocks till next consensus epoch...")
        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_best_block = sc_node_1.block_best()["result"]
        logging.info(sc_best_block)

        assert_equal(765625000, sc_best_block['block']['header']['baseFee'])

        # balance is in wei
        initial_balance = http_wallet_balance(sc_node_1, evm_address)
        assert_equal(ft_amount_in_wei, initial_balance)

        # Create an EOA to EOA transaction moving some fund to a new address not known by wallet.
        # Amount should be expressed in zennies
        transferred_amount = Decimal(12.34)
        transferred_amount_in_zennies = convertZenToZennies(transferred_amount)
        transferred_amount_in_wei = convertZenniesToWei(transferred_amount_in_zennies)

        recipient_keys = generate_account_proposition("seed3", 1)[0]
        recipient_proposition = recipient_keys.proposition
        logging.info("Trying to send {} zen to address {}".format(transferred_amount, recipient_proposition))

        j = {
            "from": evm_address,
            "to": recipient_proposition,
            "value": transferred_amount_in_zennies
        }
        request = json.dumps(j)
        response = sc_node_1.transaction_sendCoinsToAddress(request)
        logging.info("tx sent:")
        logging.info(response)
        self.sc_sync_all()

        # get mempool contents
        response_1 = allTransactions(sc_node_1)
        response_2 = allTransactions(sc_node_2)
        logging.info("mempool contents:")
        logging.info(response_1)
        assert_equal(response_1, response_2)

        # tx json repr has amount in wei
        tx_amount_in_wei = response_2["transactions"][0]["value"]
        assert_equal(str(transferred_amount_in_wei), str(tx_amount_in_wei))

        # send more zen with another from address to have more than one transaction in block
        logging.info("Trying to send {} zen to address {}".format(transferred_amount, recipient_proposition))

        j = {
            "from": evm_address_2,
            "to": recipient_proposition,
            "value": transferred_amount_in_zennies
        }
        request = json.dumps(j)
        response = sc_node_1.transaction_sendCoinsToAddress(request)
        logging.info("tx sent:")
        self.sc_sync_all()
        tx_hash = response['result']['transactionId']

        # request chainId via rpc route
        logging.info("rpc response:")
        logging.info(sc_node_1.rpc_eth_chainId())

        # request getBalance via rpc route
        logging.info("rpc response:")
        logging.info(sc_node_1.rpc_eth_getBalance(evm_hex_address, "1"))

        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()
        sc_best_block = sc_node_1.block_best()["result"]
        logging.info(sc_best_block)

        # check if header contains correct gasUsed (2 * eoa to eoa transfer gas costs)
        assert_equal(21000 * 2, sc_best_block['block']['header']['gasUsed'])

        assert_equal(669921875, sc_best_block['block']['header']['baseFee'])
        transactionFee, _, _ = computeForgedTxFee(sc_node_1, tx_hash)

        final_balance = http_wallet_balance(sc_node_1, evm_address)
        assert_equal(initial_balance - transferred_amount_in_wei - transactionFee, final_balance)


if __name__ == "__main__":
    SCEvmBootstrap().main()
