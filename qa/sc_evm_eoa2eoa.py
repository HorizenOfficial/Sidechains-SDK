#!/usr/bin/env python3
import json
import pprint
from decimal import Decimal

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, fail
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, \
    check_mainchain_block_reference_info, \
    AccountModelBlockVersion, EVM_APP_BINARY, generate_next_blocks, generate_next_block, generate_account_proposition, \
    convertZenniesToWei, convertZenToZennies, connect_sc_nodes, get_account_balance, convertZenToWei, \
    ForgerStakeSmartContractAddress, WithdrawalReqSmartContractAddress, convertWeiToZen

"""
Configuration: 
    - 2 SC nodes connected with each other
    - 1 MC node

Test:
    Test some positive scenario for the transfer of funds from EOA to EOA accounts
    Test some negative scenario too
     
"""
class SCEvmEOA2EOA(SidechainTestFramework):

    sc_nodes_bootstrap_info=None
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        print("Connecting sc nodes...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration, sc_node_2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, block_timestamp_rewind=720*120*5, blockversion=AccountModelBlockVersion)


    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              binary=[EVM_APP_BINARY]*2)#, extra_args=[['-agentlib'], []])

    def makeEoa2Eoa(self, from_sc_node, to_sc_node, from_addr, to_addr, amount_in_zen,
                    nonce = None, print_json_results = False):
        initial_balance_from = get_account_balance(from_sc_node, from_addr)
        initial_balance_to = get_account_balance(to_sc_node, to_addr)

        # Create an EOA to EOA transaction.
        # Amount should be expressed in zennies
        amount_in_zennies = convertZenToZennies(amount_in_zen)
        amount_in_wei = convertZenToWei(amount_in_zen)

        if nonce is None:
            j = {
                "from": from_addr,
                "to": to_addr,
                "value": amount_in_zennies
            }
        else:
            j = {
                "from": from_addr,
                "to": to_addr,
                "value": amount_in_zennies,
                "nonce": nonce
            }

        response = from_sc_node.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            return (False, "send failed: " + str(response))

        tx_hash = response['result']["transactionId"]
        self.sc_sync_all()

        # get mempool contents and check contents are as expected
        response = from_sc_node.transaction_allTransactions(json.dumps({"format": False}))
        assert_true(tx_hash in response['result']['transactionIds'])

        if print_json_results:
            pprint.pprint(from_sc_node.transaction_allTransactions(json.dumps({"format": True})))

        generate_next_block(from_sc_node, "first node")
        self.sc_sync_all()

        final_balance_from = get_account_balance(from_sc_node, from_addr)
        final_balance_to = get_account_balance(to_sc_node, to_addr)

        # check receipt, meanwhile do some check on amounts
        # TODO take gas into account
        receipt = from_sc_node.rpc_eth_getTransactionReceipt(tx_hash)
        if print_json_results:
            pprint.pprint(receipt)
        status = int(receipt['result']['status'], 16)
        if status == 0:
            # failed, there should be no balance modifications
            assert_equal(initial_balance_to, final_balance_to)
            assert_equal(initial_balance_from, final_balance_from)
            return (False, "receipt status FAILED")
        elif status == 1:
            # success, check we have expected balances
            if from_addr != to_addr:
                cond_to = (initial_balance_to + amount_in_wei) == final_balance_to
                cond_from = (initial_balance_from - amount_in_wei) == final_balance_from
            else:
                # using same address do not change balances
                cond_to = initial_balance_to == final_balance_to
                cond_from = initial_balance_from == final_balance_from
            assert_true(cond_to)
            assert_true(cond_from)

        return (True, "OK")


    def run_test(self):

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('500.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        self.sync_all()

        # Generate SC block and check that FT appears in SCs node wallet
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        print("Create an EOA to EOA transaction moving some fund from SC1 address to a SC2 address...")
        transferred_amount_in_zen = Decimal('11')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2, transferred_amount_in_zen, 0)
        assert_true(ret, msg)

        print("Create an EOA to EOA transaction moving some fund from SC1 address to a SC1 different address.")
        evm_address_sc1_b = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        transferred_amount_in_zen = Decimal('22')
        self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc1_b, transferred_amount_in_zen, 1)
        assert_true(ret, msg)

        print("Create an EOA to EOA transaction moving some fund from SC1 address to the same SC1 address.")
        transferred_amount_in_zen = Decimal('33')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_1, evm_address_sc1, evm_address_sc1, transferred_amount_in_zen, 2)
        assert_true(ret, msg)

        print("Create an EOA to EOA transaction with the minimum amount (1 satoshi)")
        transferred_amount_in_zen = Decimal('0.00000001')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2, transferred_amount_in_zen, 3)
        assert_true(ret, msg)

        print("Create an EOA to EOA transaction with a null value")
        transferred_amount_in_zen = Decimal('0.0')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                    transferred_amount_in_zen)
        assert_true(ret, msg)

        print("Create an EOA to EOA transaction to a not existing address")
        transferred_amount_in_zen = Decimal('1')
        not_existing_address = "63FaC9201494f0bd17B9892B9fae4d52fe3BD377"
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, not_existing_address, transferred_amount_in_zen)
        assert_true(ret, msg)

        # TODO when gas charging will be enabled, this must fail since the sender has not enough balance
        # for payoing the gas
        print("Create an EOA to EOA transaction moving all the from balance")
        transferred_amount_in_zen = convertWeiToZen(get_account_balance(sc_node_1, evm_address_sc1))
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                    transferred_amount_in_zen)
        assert_true(ret, msg)


        #negative cases

        print("Create an EOA to EOA transaction with an invalid from address (not owned) ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('1')
        not_owned_address = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, not_owned_address, evm_address_sc2, transferred_amount_in_zen)
        if not ret:
            print("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA with not owned from address should not work")


        print("Create an EOA to EOA transaction with an invalid amount (negative) ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('-0.1')
        try:
            self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2, transferred_amount_in_zen)
            fail("EOA2EOA with invalid format from address should not work")
        except Exception as e:
            print("Expected failure: {}".format(e))

        print("Create an EOA to EOA transaction moving some fund with too high a nonce ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('33')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2, transferred_amount_in_zen,
                                    33)
        if not ret:
            print("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA with bad nonce should not work")

        print("Create an EOA to EOA transaction moving some fund with too low a nonce ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('33')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                    transferred_amount_in_zen, 0)
        if not ret:
            print("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA with bad nonce should not work")

        print("Create an EOA to EOA transaction moving too large a fund ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('5678')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2, transferred_amount_in_zen)
        if not ret:
            print("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA with too big an amount should not work")

        print("Create an EOA to EOA transaction moving a fund to a fake contract address (forger stakes)  ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('1')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, ForgerStakeSmartContractAddress, transferred_amount_in_zen)
        if not ret:
            print("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA to fake smart contract should not work")

        print("Create an EOA to EOA transaction moving a fund to a fake contract address (withdrawal reqs) ==> SHOULD FAIL")
        transferred_amount_in_zen = Decimal('1')
        ret, msg = self.makeEoa2Eoa(sc_node_1, sc_node_2, evm_address_sc1, WithdrawalReqSmartContractAddress,
                                    transferred_amount_in_zen)
        if not ret:
            print("Expected failure: {}".format(msg))
        else:
            fail("EOA2EOA to fake smart contract should not work")





if __name__ == "__main__":
    SCEvmEOA2EOA().main()
