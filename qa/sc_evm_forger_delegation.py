#!/usr/bin/env python3
import json
import logging
import pprint
from decimal import Decimal

from eth_abi import decode
from eth_utils import function_signature_to_4byte_selector, encode_hex, remove_0x_prefix, add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake, format_evm
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei, convertZenToWei, \
    FORGER_STAKE_SMART_CONTRACT_ADDRESS
from SidechainTestFramework.scutil import generate_next_block, assert_true
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain, hex_str_to_bytes
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
    tot_sum = 0
    for entry in myInfoList:
        signerAddress = entry['forgerStakeData']['forgerPublicKeys']['blockSignPublicKey']['publicKey']
        if signerAddress == inSignerAddress:
            tot_sum += entry['forgerStakeData']['stakedAmount']
    # print("Sum = {}, address={}".format(sum, inSignerAddress))
    return tot_sum


def getOwnerStakeAmount(ownedStakesList, inOwnerAddress):
    tot_sum = 0
    for entry in ownedStakesList:
        ownerAddress = entry['forgerStakeData']['ownerPublicKey']['address']
        if ownerAddress == inOwnerAddress:
            tot_sum += entry['forgerStakeData']['stakedAmount']

    # print("Sum = {}, address={}".format(sum, inSignerAddress))
    return tot_sum


def get_all_owned_stakes(abi_return_value):
    # the location of the data part of the first (the only one in this case) parameter (dynamic type), measured in bytes
    # from the start of the return data block. In this case 32 (0x20)
    start_data_offset = decode(['uint32'], hex_str_to_bytes(abi_return_value[0:64]))[0] * 2
    assert_equal(start_data_offset, 64)

    end_offset = start_data_offset + 64  # read 32 bytes
    list_size = decode(['uint32'], hex_str_to_bytes(abi_return_value[start_data_offset:end_offset]))[0]

    owners_dict = {}
    for i in range(list_size):
        start_offset = end_offset
        end_offset = start_offset + 192  # read (32 + 32 + 32) bytes
        # stake id not used
        (_, value, address) = decode(['bytes32', 'uint256', 'address'],
                                     hex_str_to_bytes(abi_return_value[start_offset:end_offset]))
        sc_address_checksum_fmt = address  # to_checksum_address(address)
        print("sc addr=" + sc_address_checksum_fmt)
        if sc_address_checksum_fmt in owners_dict:
            val = owners_dict.get(sc_address_checksum_fmt)
        else:
            owners_dict[sc_address_checksum_fmt] = 0
            val = 0

        owners_dict[sc_address_checksum_fmt] = val + value

        start_offset = end_offset
        end_offset = start_offset + 192  # read (32 + 32 + 32) bytes
        # these are blockSignerPubKey and vrfKey 33 bytes
        (_, _, _) = decode(['bytes32', 'bytes32', 'bytes1'],
                           hex_str_to_bytes(abi_return_value[start_offset:end_offset]))
    pprint.pprint(owners_dict)
    return owners_dict


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

        block_number_x = sc_node_1.rpc_eth_blockNumber()["result"]

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

        ownerAddress = {"ownerAddress": add_0x_prefix(evm_address_sc_node_1)}
        ownedStakesList = sc_node_3.transaction_ownedForgingStakes(json.dumps(ownerAddress))['result']['stakes']
        pprint.pprint(ownedStakesList)
        sumOwned = getOwnerStakeAmount(ownedStakesList, evm_address_sc_node_1)
        tot_owned_addr_1 = convertZenToWei(forgerStake12_amount + forgerStake13_amount)
        assert_equal(sumOwned, tot_owned_addr_1)

        # call nsc for getting all forger stakes
        method = 'getAllForgersStakes()'
        abi_str = function_signature_to_4byte_selector(method)
        req = {
            "from": format_evm(evm_address_sc_node_1),
            "to": format_evm(FORGER_STAKE_SMART_CONTRACT_ADDRESS),
            "value": 0,
            "data": encode_hex(abi_str)
        }
        response = sc_node_1.rpc_eth_call(req, 'latest')
        abi_return_value = remove_0x_prefix(response['result'])
        print(abi_return_value)
        # decode the ABI list creating a dictionary ownerAddress/value, and verify we have the expected amount for
        # the first owner address
        ownersStakeList = get_all_owned_stakes(abi_return_value)
        assert_equal(ownersStakeList[add_0x_prefix(evm_address_sc_node_1)], tot_owned_addr_1)

        balance = int(
            sc_node_1.rpc_eth_getBalance(add_0x_prefix(FORGER_STAKE_SMART_CONTRACT_ADDRESS), 'latest')['result'], 16)
        print("balances at latest: nsc bal={}".format(balance))

        # test that eth call works with a reference block leaving in the past. At that block we should have only the
        # genesis stake and not any stake for the owner address
        response = sc_node_1.rpc_eth_call(req, block_number_x)
        abi_return_value = remove_0x_prefix(response['result'])
        print(abi_return_value)
        ownersStakeList = get_all_owned_stakes(abi_return_value)
        assert_true(add_0x_prefix(evm_address_sc_node_1) not in ownersStakeList)
        balance = int(
            sc_node_1.rpc_eth_getBalance(add_0x_prefix(FORGER_STAKE_SMART_CONTRACT_ADDRESS), block_number_x)['result'],
            16)
        print("balances at {}: nsc bal={}".format(block_number_x, balance))
        print("gen staked amount = {}".format(genStakeAmount))
        assert_equal(convertZenniesToWei(genStakeAmount), balance)


if __name__ == "__main__":
    SCEvmForgerDelegation().main()
