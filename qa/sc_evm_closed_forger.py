#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from eth_utils import add_0x_prefix

from SidechainTestFramework.sc_boostrap_info import (
    LARGE_WITHDRAWAL_EPOCH_LENGTH, MCConnectionInfo, SCCreationInfo,
    SCForgerConfiguration, SCNetworkConfiguration, SCNodeConfiguration,
)
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import (
    AccountModelBlockVersion, EVM_APP_BINARY, bootstrap_sidechain_nodes,
    connect_sc_nodes, convertZenToWei, convertZenToZennies, generate_next_block, generate_secrets, generate_vrf_secrets,
    start_sc_nodes, SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME,
)
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from test_framework.util import (
    assert_equal, assert_false, assert_true, forward_transfer_to_sidechain, start_nodes,
    websocket_port_by_mc_node_index,
)

"""
Check the EVM bootstrap feature.

Configuration: 
    - 2 SC nodes configured with a closed list of forger, connected with each other
    - 1 MC node

Test:
    - Try to stake money with invalid forger info and verify that we are not allowed to stake
    - Try the same with forger info pubkeys contained in the closed list, should be succesful


"""


class SCEvmClosedForgerList(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    sc_creation_amount = 100
    API_KEY = "Horizen"

    allowed_forger_block_signer_public_key = generate_secrets("seed_new", 1)[0].publicKey
    allowed_forger_vrf_public_key = generate_vrf_secrets("seed_new", 1)[0].publicKey

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_network(self, split=False):
        self.sc_nodes = self.sc_setup_nodes()
        logging.info("Connecting sc nodes...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]

        forger_configuration  = SCForgerConfiguration(True, [
            [self.allowed_forger_block_signer_public_key, self.allowed_forger_vrf_public_key]
        ])

        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            forger_options = forger_configuration,
            api_key = self.API_KEY
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            forger_options = forger_configuration,
            api_key = self.API_KEY
        )
        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, self.sc_creation_amount, LARGE_WITHDRAWAL_EPOCH_LENGTH),
            sc_node_1_configuration, sc_node_2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 10,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              auth_api_key=self.API_KEY,
                              binary=[EVM_APP_BINARY] * 2)#, extra_args=[['-agentlib'], []])

    def tryMakeForgetStake(self, sc_node, owner_address, blockSignPubKey, vrf_public_key, amount):
        # a transaction with a forger stake info not compliant with the closed forger list will be successfully
        # included in a block but the receipt will then report a 'failed' status.
        forgerStakes = {"forgerStakeInfo": {
                "ownerAddress": owner_address,  # SC node 1 is an owner
                "blockSignPublicKey": blockSignPubKey,
                "vrfPubKey": vrf_public_key,
                "value": convertZenToZennies(amount)  # in Satoshi
            }
        }
        makeForgerStakeJsonRes = sc_node.transaction_makeForgerStake(json.dumps(forgerStakes))
        assert_true("result" in makeForgerStakeJsonRes)
        #logging.info(json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # check we had a failure in the receipt
        tx_hash = makeForgerStakeJsonRes['result']["transactionId"]
        logging.info("Getting receipt for txhash={}".format(tx_hash))

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        # status == 1 is succesful
        return (status == 1)

    def run_test(self):

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(stakeList), 1)

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc_node_1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('500.0')
        ft_amount_in_wei = convertZenToWei(ft_amount_in_zen)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        self.sync_all()

        # Generate SC block and check that FT appears in SCs node wallet
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        initial_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(ft_amount_in_wei, initial_balance)

        # generate publick keys not contained in the closed forger list
        outlaw_blockSignPubKey = sc_node_1.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        outlaw_vrfPubKey = sc_node_1.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        # Try to stake to an invalid blockSignProposition
        logging.info("Try to stake to an invalid blockSignProposition...")
        result = self.tryMakeForgetStake(
              sc_node_1, evm_address_sc_node_1, outlaw_blockSignPubKey,
              self.allowed_forger_vrf_public_key, amount=33)
        assert_false(result)

        # Try to stake to an invalid vrfPublicKey
        logging.info("Try to stake to an invalid vrfPublicKey...")
        result = self.tryMakeForgetStake(
              sc_node_1, evm_address_sc_node_1, self.allowed_forger_block_signer_public_key,
              outlaw_vrfPubKey, amount=33)
        assert_false(result)

        # Try to stake with an invalid blockSignProposition and an invalid vrfPublicKey
        logging.info("Try to stake to an invalid blockSignProposition and an invalid vrfPublicKey...")
        result = self.tryMakeForgetStake(
              sc_node_1, evm_address_sc_node_1, outlaw_blockSignPubKey,
              outlaw_vrfPubKey, amount=33)
        assert_false(result)

        # Try to stake with a valid blockSignProposition and valid vrfPublicKey
        logging.info("Try to stake to a valid blockSignProposition and valid vrfPublicKey...")
        result = self.tryMakeForgetStake(
              sc_node_1, evm_address_sc_node_1, self.allowed_forger_block_signer_public_key,
              self.allowed_forger_vrf_public_key, amount=33)
        assert_true(result)




if __name__ == "__main__":
    SCEvmClosedForgerList().main()
