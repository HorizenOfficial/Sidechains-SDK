#!/usr/bin/env python3
import json
import logging
import pprint

from decimal import Decimal
from SidechainTestFramework.sc_boostrap_info import (
    LARGE_WITHDRAWAL_EPOCH_LENGTH, MCConnectionInfo, SCCreationInfo,
    SCNetworkConfiguration, SCNodeConfiguration
)
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import (
    AccountModelBlockVersion, EVM_APP_BINARY, bootstrap_sidechain_nodes, connect_sc_nodes,
    convertZenToZennies, generate_next_block, start_sc_nodes
)
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain, start_nodes, websocket_port_by_mc_node_index
)

"""
Configuration: 
    - 3 SC nodes connected with each other
    - 1 MC node
    - SC1 node owns a stakeAmount made out of cross chain creation output

Test:
    - Send FTs to SC1, SC2 and SC3 (used for forging delegation)
    - SC1 delegates to SC2
    - SC1 delegates to SC3
    - SC3 delegates to SC2 with the same block/vrf pub key as SC1 delegation
    - SC2 delegates to itself with the same block/vrf pub key as SC1 and SC3 delegation
    - Check there are a total of 5 stakes id (genesis creation + 4 delegations)
    - Check there are a total of 3 forging stake info grouped by block/vrf pub key and with the
      expected order and total delegated stake


"""


class SCEvmForgerDelegation(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 3
    sc_creation_amount = 99

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_network(self, split=False):
        self.sc_nodes = self.sc_setup_nodes()
        logging.info("Connecting sc nodes...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        connect_sc_nodes(self.sc_nodes[1], 2)
        connect_sc_nodes(self.sc_nodes[2], 0)
        self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_3_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, self.sc_creation_amount, LARGE_WITHDRAWAL_EPOCH_LENGTH),
            sc_node_1_configuration, sc_node_2_configuration, sc_node_3_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=720 * 120 * 10,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              binary=[EVM_APP_BINARY] * self.number_of_sidechain_nodes)#, extra_args=[['-agentlib'], [], []])

    def run_test(self):

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        sc_node_3 = self.sc_nodes[2]

        evm_address_sc_node_1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc_node_3 = sc_node_3.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # get stake info from genesis block
        sc_genesis_block = sc_node_1.block_best()
        genStakeInfo = sc_genesis_block["result"]["block"]["header"]["forgingStakeInfo"]
        genStakeAmount = genStakeInfo['stakeAmount']
        sc1_blockSignPubKey = genStakeInfo["blockSignPublicKey"]["publicKey"]
        sc1_vrfPubKey = genStakeInfo["vrfPublicKey"]["publicKey"]

        ft_amount_in_zen = Decimal('100.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_3,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        # Generate SC block
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        # SC1 delegates 50 zen to SC2
        forgerStake12_amount = 50
        forgerStakes = {
            "forgerStakeInfo": {
                "ownerAddress": evm_address_sc_node_1,
                "blockSignPublicKey": sc2_blockSignPubKey,
                "vrfPubKey": sc2_vrfPubKey,
                "value": convertZenToZennies(forgerStake12_amount)
            }
        }

        result = sc_node_1.transaction_makeForgerStake(json.dumps(forgerStakes))
        if "result" not in result:
            fail("make forger stake failed: " + json.dumps(result))
        else:
            logging.info("Forger stake created: " + json.dumps(result))
        self.sc_sync_all()

        sc3_blockSignPubKey = sc_node_3.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc3_vrfPubKey = sc_node_3.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        # SC1 delegates 4 zen to SC3
        forgerStake13_amount = 4
        forgerStakes = {
            "forgerStakeInfo": {
                "ownerAddress": evm_address_sc_node_1,
                "blockSignPublicKey": sc3_blockSignPubKey,
                "vrfPubKey": sc3_vrfPubKey,
                "value": convertZenToZennies(forgerStake13_amount)
            },
            "nonce": 1  # second tx from this evm address
        }

        result = sc_node_1.transaction_makeForgerStake(json.dumps(forgerStakes))
        if "result" not in result:
            fail("make forger stake failed: " + json.dumps(result))
        else:
            logging.info("Forger stake created: " + json.dumps(result))
        self.sc_sync_all()

        # SC3 delegates 60 zen to SC2
        forgerStake32_amount = 60  # Zen
        forgerStakes = {
            "forgerStakeInfo": {
                "ownerAddress": evm_address_sc_node_3,  # SC node 3 is an owner
                "blockSignPublicKey": sc2_blockSignPubKey,  # SC node 1 is a block signer
                "vrfPubKey": sc2_vrfPubKey,
                "value": convertZenToZennies(forgerStake32_amount)  # in Satoshi
            }
        }

        result = sc_node_3.transaction_makeForgerStake(json.dumps(forgerStakes))
        if "result" not in result:
            fail("make forger stake failed: " + json.dumps(result))
        else:
            logging.info("Forger stake created: " + json.dumps(result))
        self.sc_sync_all()

        # SC2 delegates 33 zen to itself
        forgerStake22_amount = 33  # Zen
        forgerStakes = {
            "forgerStakeInfo": {
                "ownerAddress": evm_address_sc_node_2,  # SC node 1 is an owner
                "blockSignPublicKey": sc2_blockSignPubKey,  # SC node 1 is a block signer
                "vrfPubKey": sc2_vrfPubKey,
                "value": convertZenToZennies(forgerStake22_amount)  # in Satoshi
            }
        }

        result = sc_node_2.transaction_makeForgerStake(json.dumps(forgerStakes))
        if "result" not in result:
            fail("make forger stake failed: " + json.dumps(result))
        else:
            logging.info("Forger stake created: " + json.dumps(result))
        self.sc_sync_all()

        # get mempool contents, we must have 4 forger stake txes
        mempoolList = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))['result']['transactionIds']
        assert_equal(4, len(mempoolList))

        # Generate SC block
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # we have a total of 5 stake ids, the genesis creation and the 4 txes just forged
        stakeList = sc_node_1.transaction_allForgingStakes()['result']['stakes']
        #pprint.pprint(stakeList)
        assert_equal(5, len(stakeList))

        # we have 3 entries in the forging stake info list, grouped by blockSignPublicKey/vrfPubKey pairs and sorted by total delegated
        # stake amount in decreasing order
        stakeInfoList = sc_node_1.transaction_allActiveForgingStakeInfo()['result']['stakes']
        assert_equal(3, len(stakeInfoList))

        # the head of this ordered list is the delegation made by SC1/2/3 to SC2 (total of 143 zen)
        stake_123_to_2 = stakeInfoList[0]['stakeAmount']
        assert_equal(stake_123_to_2, convertZenToZennies(forgerStake12_amount+forgerStake22_amount+forgerStake32_amount))

        # the middle of this ordered list is the delegation made by genesis creation to SC1 (99 zen)
        stake_gen_to_1 = stakeInfoList[1]['stakeAmount']
        assert_equal(stake_gen_to_1, genStakeAmount)

        # the tail of this ordered list is the delegation made by SC1 to SC3 (total of 4 zen)
        stake_1_to_3 = stakeInfoList[2]['stakeAmount']
        assert_equal(stake_1_to_3, convertZenToZennies(forgerStake13_amount))

        # Generate an SC block
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # check block forged by SC 1 has expected forging stake info
        best_block = sc_node_3.block_best()
        blockStakeInfo = best_block["result"]["block"]["header"]["forgingStakeInfo"]
        assert_equal(blockStakeInfo['stakeAmount'], stake_gen_to_1)
        assert_equal(blockStakeInfo['blockSignPublicKey']['publicKey'], sc1_blockSignPubKey)
        assert_equal(blockStakeInfo['vrfPublicKey']['publicKey'], sc1_vrfPubKey)

        # Generate an SC block
        generate_next_block(sc_node_2, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # check block forged by SC 2 has expected forging stake info
        best_block = sc_node_3.block_best()
        blockStakeInfo = best_block["result"]["block"]["header"]["forgingStakeInfo"]
        assert_equal(blockStakeInfo['stakeAmount'], stake_123_to_2)
        assert_equal(blockStakeInfo['blockSignPublicKey']['publicKey'], sc2_blockSignPubKey)
        assert_equal(blockStakeInfo['vrfPublicKey']['publicKey'], sc2_vrfPubKey)

        # SC 1 is owner of 3 delegated stakes
        myInfoList = sc_node_1.transaction_myForgingStakes()['result']['stakes']
        assert_equal(3, len(myInfoList))

        # SC 2 is owner of 1 delegated stake
        myInfoList = sc_node_2.transaction_myForgingStakes()['result']['stakes']
        assert_equal(1, len(myInfoList))

        # SC 3 is owner of 1 delegated stake
        myInfoList = sc_node_3.transaction_myForgingStakes()['result']['stakes']
        assert_equal(1, len(myInfoList))

        # SC 1 can forge with 1 delegation group
        myInfoList = sc_node_1.transaction_myActiveForgingStakeInfo()['result']['stakes']
        assert_equal(1, len(myInfoList))
        assert_equal(myInfoList[0]['stakeAmount'], stake_gen_to_1)

        # SC 2 can forge with 1 delegation group
        myInfoList = sc_node_2.transaction_myActiveForgingStakeInfo()['result']['stakes']
        assert_equal(1, len(myInfoList))
        assert_equal(myInfoList[0]['stakeAmount'], stake_123_to_2)

        # SC 3 can forge with 1 delegation group
        myInfoList = sc_node_3.transaction_myActiveForgingStakeInfo()['result']['stakes']
        assert_equal(1, len(myInfoList))
        assert_equal(myInfoList[0]['stakeAmount'], stake_1_to_3)





if __name__ == "__main__":
    SCEvmForgerDelegation().main()
