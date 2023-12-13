#!/usr/bin/env python3
import logging
from decimal import Decimal

from eth_utils import add_0x_prefix, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createLegacyEIP155Transaction import \
    createLegacyEIP155Transaction
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.scutil import generate_next_block

from httpCalls.transaction.allTransactions import allTransactions
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenToWei, convertWeiToZen, \
    FORGER_STAKE_SMART_CONTRACT_ADDRESS, WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS
from test_framework.util import (
    assert_equal, assert_true, fail, )

"""
Configuration: 
    - 2 SC nodes connected with each other
    - 1 MC node

Test:
    Test some positive scenario for the transfer of funds from EOA to EOA accounts
    Test some negative scenario too
     
"""


class SCEvmEOA2EOA(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def makeEoa2Eoa(self, from_sc_node, to_sc_node, from_addr, to_addr, amount_in_zen, *,
                    nonce=None, isEIP155=False, print_json_results=False):
        initial_balance_from = http_wallet_balance(from_sc_node, from_addr)
        initial_balance_to = http_wallet_balance(to_sc_node, to_addr)

        # Create an EOA to EOA transaction.
        # Amount should be expressed in zennies
        amount_in_wei = convertZenToWei(amount_in_zen)

        try:
            if isEIP155:
                tx_hash = createLegacyEIP155Transaction(from_sc_node,
                    fromAddress=from_addr,
                    toAddress=to_addr,
                    value=amount_in_wei,
                    nonce=nonce
                )
            else:
                tx_hash = createLegacyTransaction(from_sc_node,
                    fromAddress=from_addr,
                    toAddress=to_addr,
                    value=amount_in_wei,
                    nonce=nonce
                )
        except RuntimeError as err:
            logging.info("Expected exception thrown: {}".format(err))
            return False, "send failed: " + str(err), None

        self.sc_sync_all()

        # get mempool contents and check contents are as expected
        response = allTransactions(from_sc_node, False)
        assert_true(tx_hash in response['transactionIds'])

        if print_json_results:
            logging.info(allTransactions(from_sc_node))

        generate_next_block(from_sc_node, "first node")
        self.sc_sync_all()

        final_balance_from = http_wallet_balance(from_sc_node, from_addr)
        final_balance_to = http_wallet_balance(to_sc_node, to_addr)

        # check receipt, meanwhile do some check on amounts
        receipt = from_sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        if print_json_results:
            logging.info(receipt)
        status = int(receipt['result']['status'], 16)
        gasUsed = int(receipt['result']['gasUsed'][2:], 16) * int(receipt['result']['effectiveGasPrice'][2:], 16)
        if status == 0:
            assert_equal(initial_balance_to, final_balance_to)
            assert_equal(initial_balance_from - gasUsed, final_balance_from)
            return False, "receipt status FAILED", tx_hash
        elif status == 1:

            # success, check we have expected balances
            if from_addr != to_addr:
                cond_to = (initial_balance_to + amount_in_wei) == final_balance_to
                cond_from = (initial_balance_from - amount_in_wei - gasUsed) == final_balance_from
            else:
                # using same address do not change balances
                cond_to = (initial_balance_to - gasUsed) == final_balance_to
                cond_from = (initial_balance_from - gasUsed) == final_balance_from
            assert_true(cond_to)
            assert_true(cond_from)

        return True, "OK", tx_hash

    def run_test(self):

        ft_amount_in_zen = Decimal('500.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = remove_0x_prefix(self.evm_address)
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        logging.info("Create an EOA to EOA transaction moving some fund from SC1 address to a SC2 address...")
        transferred_amount_in_zen = Decimal('11')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                       transferred_amount_in_zen)
        assert_true(ret, msg)

        logging.info("Repeat similar transaction with different case in sender address...")
        transferred_amount_in_zen = Decimal('11')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1.upper(), evm_address_sc2,
                                       transferred_amount_in_zen)
        assert_true(ret, msg)

        logging.info("Create an EOA to EOA transaction moving some fund from SC1 address to a SC1 different address.")
        evm_address_sc1_b = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        transferred_amount_in_zen = Decimal('22')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc1_b,
                                       transferred_amount_in_zen)
        assert_true(ret, msg)

        logging.info("Create an EOA to EOA transaction moving some fund from SC1 address to the same SC1 address.")
        transferred_amount_in_zen = Decimal('33')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_1, evm_address_sc1, evm_address_sc1,
                                       transferred_amount_in_zen)
        assert_true(ret, msg)

        logging.info("Create an EOA to EOA transaction with the minimum amount (1 satoshi)")
        transferred_amount_in_zen = Decimal('0.00000001')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                       transferred_amount_in_zen)
        assert_true(ret, msg)

        logging.info("Create an EOA to EOA transaction with a null value")
        transferred_amount_in_zen = Decimal('0.0')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                       transferred_amount_in_zen)
        assert_true(ret, msg)

        logging.info("Create an EOA to EOA transaction to a not existing address")
        transferred_amount_in_zen = Decimal('1')
        not_existing_address = "63FaC9201494f0bd17B9892B9fae4d52fe3BD377"
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, not_existing_address,
                                       transferred_amount_in_zen)
        assert_true(ret, msg)

        logging.info("Create an EOA to EOA EIP155 transaction moving some fund from SC1 address to a SC2 address...")
        transferred_amount_in_zen = Decimal('15')
        ret, msg, txHash = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                            transferred_amount_in_zen,
                                            isEIP155=True, print_json_results=False)
        assert_true(ret, msg)

        # moreover, check we have the chainId in tx json, as per EIP155
        txJsonResult = sc_node_1.rpc_eth_getTransactionByHash(add_0x_prefix(txHash))['result']
        assert_true("chainId" in txJsonResult)

        logging.info("Repeat similar transaction with different case in sender address...")
        transferred_amount_in_zen = Decimal('15')
        ret, msg, txHash = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1.upper(), evm_address_sc2,
                                            transferred_amount_in_zen,
                                            isEIP155=True, print_json_results=False)
        assert_true(ret, msg)

        # negative cases

        logging.info("Create an EOA to EOA transaction moving all the from balance")
        transferred_amount_in_zen = convertWeiToZen(http_wallet_balance(sc_node_1, evm_address_sc1))
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                       transferred_amount_in_zen)
        if not ret:
            logging.info("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA of the whole balance should not work due to gas consummation")

        logging.info("Create an EOA to EOA transaction with an invalid from address (not owned) ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('1')
        not_owned_address = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, not_owned_address, evm_address_sc2,
                                       transferred_amount_in_zen)
        if not ret:
            logging.info("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA with not owned from address should not work")

        logging.info("Create an EOA to EOA transaction with an invalid amount (negative) ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('-0.1')
        try:
            self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2, transferred_amount_in_zen)
            fail("EOA2EOA with invalid format from address should not work")
        except Exception as e:
            logging.info("Expected failure: {}".format(e))

        logging.info("Create an EOA to EOA transaction moving some fund with too low a nonce ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('33')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                       transferred_amount_in_zen, nonce=0)
        if not ret:
            logging.info("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA with bad nonce should not work")

        logging.info("Create an EOA to EOA transaction moving too large a fund ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('5678')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                       transferred_amount_in_zen)
        if not ret:
            logging.info("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA with too big an amount should not work")

        logging.info(
            "Create an EOA to EOA transaction moving a fund to a native contract address (forger stakes)  ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('1')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                       transferred_amount_in_zen)
        if not ret:
            logging.info("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA to native smart contract should not work")

        logging.info(
            "Create an EOA to EOA transaction moving a fund to a native contract address (withdrawal reqs) ==> SHOULD "
            "FAIL")
        transferred_amount_in_zen = Decimal('1')
        ret, msg, _ = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS,
                                       transferred_amount_in_zen)
        if not ret:
            logging.info("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA to native smart contract should not work")


if __name__ == "__main__":
    SCEvmEOA2EOA().main()
