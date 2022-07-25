#!/usr/bin/env python3
import json
import pprint
from decimal import Decimal

import test_framework.util
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.mk_contract_address import mk_contract_address
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_forging_util import sc_create_forging_stake_mempool
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, COIN
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, is_mainchain_block_included_in_sc_block, check_box_balance, \
    check_mainchain_block_reference_info, check_wallet_coins_balance, check_box_balance, get_lib_separator, \
    AccountModelBlockVersion, EVM_APP_BINARY, generate_next_blocks, generate_next_block, generate_account_proposition, \
    convertZenniesToWei, convertZenToZennies

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


class SCEvmBootstrap(SidechainTestFramework):
    sc_nodes_bootstrap_info = None

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_network(self, split=False):
        self.sc_nodes = self.sc_setup_nodes()
        print("...skip sync since it would timeout as of now")
        # self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=720 * 120 * 5,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(num_nodes=1, dirname=self.options.tmpdir,
                              binary=[EVM_APP_BINARY])  # , extra_args=['-agentlib'])

    def run_test(self):
        sc_node = self.sc_nodes[0]
        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = self.nodes[0].getblock(mc_block["hash"], False)
        print("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node.block_best()["result"]

        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        mc_return_address = self.nodes[0].getnewaddress()
        # evm_address = generate_account_proposition("seed2", 1)[0]

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        pprint.pprint(ret)
        evm_address = ret["result"]["proposition"]["address"]
        print("pubkey = {}".format(evm_address))

        # call a legacy wallet api
        ret = sc_node.wallet_allPublicKeys()
        pprint.pprint(ret)

        ft_amount_in_zen = Decimal("33.22")
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      evm_address,
                                      ft_amount_in_zen,
                                      mc_return_address)

        # input("\n\t======> Enter any input to continue generating a new sc block...")
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()
        sc_best_block = sc_node.block_best()["result"]
        assert_equal(sc_best_block["height"], 2, "The best block has not the specified height.")
        pprint.pprint(sc_best_block)
        pprint.pprint(sc_node.rpc_eth_getBalance(str(evm_address), "1"))

        '''
        # TODO
        # Make and activate forging stake for the SC node 1
        stake_amount = 1.234
        sc_create_forging_stake_mempool(sc_node, stake_amount)
        self.sc_sync_all()  # Sync SC nodes mempools
        # Generate SC block with ForgerStake creation TX
        generate_next_block(sc_node, "first node")
        '''

        # input("\n\t======> Enter any input to continue generating blocks till next consensus epoch...")
        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_best_block = sc_node.block_best()["result"]
        pprint.pprint(sc_best_block)

        j = {"address": str(evm_address)}
        balance_request = json.dumps(j)

        # balance is in wei
        initial_balance = sc_node.wallet_getBalance(balance_request)["result"]["balance"]
        assert_equal(ft_amount_in_wei, initial_balance)

        # Create an EOA to EOA transaction moving some fund to a new address not known by wallet.
        # Amount should be expressed in zennies
        transferred_amount = Decimal(12.34)
        transferred_amount_in_zennies = convertZenToZennies(transferred_amount)
        transferred_amount_in_wei = convertZenniesToWei(transferred_amount_in_zennies)

        recipientKeys = generate_account_proposition("seed3", 1)[0]
        print("Trying to send {} zen to address {}".format(transferred_amount, recipientKeys.proposition))

        j = {
            "from": str(evm_address),
            "to": recipientKeys.proposition,
            "value": transferred_amount_in_zennies
        }
        request = json.dumps(j)
        response = sc_node.transaction_sendCoinsToAddress(request)
        print("tx sent:")
        pprint.pprint(response)

        # get mempool contents
        response = sc_node.transaction_allTransactions()
        print("mempool contents:")
        pprint.pprint(response)

        # tx json repr has amount in wei
        tx_amount_in_wei = response["result"]["transactions"][0]["value"]
        assert_equal(str(tx_amount_in_wei), str(transferred_amount_in_wei))

        # request chainId via rpc route
        print("rpc response:")
        pprint.pprint(sc_node.rpc_eth_chainId())

        # request getBalance via rpc route
        print("rpc response:")
        pprint.pprint(sc_node.rpc_eth_getBalance(str(evm_address), "1"))

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()
        sc_best_block = sc_node.block_best()["result"]
        pprint.pprint(sc_best_block)

        final_balance = sc_node.wallet_getBalance(balance_request)["result"]["balance"]
        assert_equal(initial_balance - transferred_amount_in_wei, final_balance)

        smart_contract = SmartContract('StorageTestContract')
        print(smart_contract)
        # TODO fix rpc getTransactionCount
        # currently we know the nonce is 2 here, but it would be easier if this could be automated
        # nonce_response = sc_node.rpc_eth_getTransactionCount(str(evm_address), '0')
        # pprint.pprint(nonce_response)
        nonce = 2
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node, fromAddress=str(evm_address), nonce=nonce,
                                                                gasLimit=100000000,
                                                                gasPrice=10)
        nonce = nonce + 1
        generate_next_blocks(sc_node, "first node", 1)
        print("Blocks mined - tx receipt will contain address")
        # TODO fix receipts, currently empty
        print("rpc response:")
        pprint.pprint(sc_node.rpc_eth_getTransactionReceipt(tx_hash))

        smart_contract.call_function(sc_node, 'set(string)', 'This is a message', fromAddress=str(evm_address),
                                     nonce=nonce,
                                     gasLimit=10000000, gasPrice=10, toAddress=smart_contract_address)

        generate_next_blocks(sc_node, "first node", 1)
        # TODO eth_call to read the value and make sure it is "This is a message"
        # Currently rpc doesn't do anything
        res = smart_contract.static_call(sc_node, 'get()',
                                         fromAddress=str(evm_address),
                                         nonce=nonce, gasLimit=10000000,
                                         gasPrice=10, toAddress=smart_contract_address)


if __name__ == "__main__":
    SCEvmBootstrap().main()
