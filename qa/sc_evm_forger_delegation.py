#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain
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
    - Check that forged block headers have the expected forging stake amount


"""


def getSignerStakeAmount(myInfoList, inSignerAddress):
    sum = 0
    for entry in myInfoList:
        signerAddress = entry['forgerStakeData']['forgerPublicKeys']['blockSignPublicKey']['publicKey']
        if signerAddress == inSignerAddress:
            sum += entry['forgerStakeData']['stakedAmount']
    # print("Sum = {}, address={}".format(sum, inSignerAddress))
    return sum


class SCEvmForgerDelegation(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=3, forward_amount=99, block_timestamp_rewind=720 * 120 * 10)

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

        result = ac_makeForgerStake(sc_node_1, evm_address_sc_node_1, sc2_blockSignPubKey, sc2_vrfPubKey,
                                    convertZenToZennies(forgerStake12_amount))
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

        result = ac_makeForgerStake(sc_node_1, evm_address_sc_node_1, sc3_blockSignPubKey, sc3_vrfPubKey,
                                    convertZenToZennies(forgerStake13_amount), 1)
        # result = sc_node_1.transaction_makeForgerStake(json.dumps(forgerStakes))
        if "result" not in result:
            fail("make forger stake failed: " + json.dumps(result))
        else:
            logging.info("Forger stake created: " + json.dumps(result))
        self.sc_sync_all()

        # SC3 delegates 60 zen to SC2
        forgerStake32_amount = 60  # Zen

        result = ac_makeForgerStake(sc_node_3, evm_address_sc_node_3, sc2_blockSignPubKey, sc2_vrfPubKey,
                                    convertZenToZennies(forgerStake32_amount))
        if "result" not in result:
            fail("make forger stake failed: " + json.dumps(result))
        else:
            logging.info("Forger stake created: " + json.dumps(result))
        self.sc_sync_all()

        # SC2 delegates 33 zen to itself
        forgerStake22_amount = 33  # Zen

        result = ac_makeForgerStake(sc_node_2, evm_address_sc_node_2, sc2_blockSignPubKey, sc2_vrfPubKey,
                                    convertZenToZennies(forgerStake22_amount))
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

        # SC 1 is owner of 3 delegated stakes
        myInfoList = sc_node_1.transaction_myForgingStakes()['result']['stakes']
        assert_equal(3, len(myInfoList))

        # SC 2 is owner of 1 delegated stake
        myInfoList = sc_node_2.transaction_myForgingStakes()['result']['stakes']
        assert_equal(1, len(myInfoList))

        # SC 3 is owner of 1 delegated stake
        myInfoList = sc_node_3.transaction_myForgingStakes()['result']['stakes']
        assert_equal(1, len(myInfoList))

        # we have a total of 5 stake ids, the genesis creation and the 4 txes just forged
        stakeList = sc_node_1.transaction_allForgingStakes()['result']['stakes']
        # pprint.pprint(stakeList)
        assert_equal(5, len(stakeList))

        # take amounts of forger block signers
        sum1 = getSignerStakeAmount(stakeList, sc1_blockSignPubKey)
        sum2 = getSignerStakeAmount(stakeList, sc2_blockSignPubKey)
        sum3 = getSignerStakeAmount(stakeList, sc3_blockSignPubKey)

        # delegation made by genesis creation to SC1 (99 zen)
        stake_gen_to_1 = genStakeAmount

        # delegation made by SC1/2/3 to SC2 (total of 143 zen)
        stake_123_to_2 = convertZenToZennies(forgerStake12_amount + forgerStake22_amount + forgerStake32_amount)

        # delegation made by SC1 to SC3 (total of 4 zen)
        stake_1_to_3 = convertZenToZennies(forgerStake13_amount)

        assert_equal(convertZenniesToWei(stake_gen_to_1), sum1)
        assert_equal(convertZenniesToWei(stake_123_to_2), sum2)
        assert_equal(convertZenniesToWei(stake_1_to_3), sum3)

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


if __name__ == "__main__":
    SCEvmForgerDelegation().main()
