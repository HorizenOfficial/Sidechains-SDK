#!/usr/bin/env python3
import json
import logging
import pprint
import time
from decimal import Decimal

from eth_abi import decode
from eth_utils import add_0x_prefix, remove_0x_prefix, encode_hex, event_signature_to_log_topic, to_hex
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake
from SidechainTestFramework.account.httpCalls.transaction.openForgerList import open_forger_list
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.sc_boostrap_info import SCForgerConfiguration
from SidechainTestFramework.scutil import (generate_next_block, generate_secrets, generate_vrf_secrets,
                                           SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME,
                                           )
from test_framework.util import (
    assert_equal, assert_false, assert_true, forward_transfer_to_sidechain, hex_str_to_bytes, )

"""
Check the open forger list feature.

Configuration: 
    - 2 SC nodes configured with a closed list of forger, connected with each other
    - 1 MC node

Test:
    - Try to stake money with invalid forger info and verify that we are not allowed to stake
    - Try the same with forger info pubkeys contained in the closed list, should be succesful
    - Open the stake to the world using the openStakeTransaction and verify that a generic proposition (not included in the forger list) 
      is allowed to forge. Some negative test is also done.


"""


def check_open_forger_list_event(event, forgerIndex):
    assert_equal(2, len(event['topics']), "Wrong number of topics in event")
    event_id = remove_0x_prefix(event['topics'][0])
    event_signature = remove_0x_prefix(
        encode_hex(event_signature_to_log_topic('OpenForgerList(uint32,address,bytes32)')))
    assert_equal(event_signature, event_id, "Wrong event signature in topics")

    index = decode(['uint32'], hex_str_to_bytes(event['topics'][1][2:]))[0]
    logging.info("event: forgerIndex={}".format(index))
    assert_equal(index, forgerIndex, "Wrong from address in topics")

    (addr, pkey) = decode(['address', 'bytes32'], hex_str_to_bytes(event['data'][2:]))
    logging.info("event: addr={}, blockSignPkey={}".format(addr, to_hex(pkey)))



class SCEvmClosedForgerList(AccountChainSetup):

    number_of_sidechain_nodes = 2

    # the genesis keys are added to the list of allowed forgers, therefore we have a total of 3 allowed forgers
    number_of_allowed_forgers = 2

    allowed_forger_propositions = generate_secrets("seed_2", number_of_allowed_forgers)
    allowed_forger_vrf_public_keys = generate_vrf_secrets("seed_2", number_of_allowed_forgers)


    def __init__(self):
        allowedForgers = []
        for i in range (0, self.number_of_allowed_forgers):
            allowedForgers += [[self.allowed_forger_propositions[i].publicKey, self.allowed_forger_vrf_public_keys[i].publicKey]]

        forger_configuration  = SCForgerConfiguration(True, allowedForgers)

        super().__init__(number_of_sidechain_nodes=self.number_of_sidechain_nodes, forward_amount=100,
                         block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 10,
                         forger_options=forger_configuration,
                         initial_private_keys=list(map(lambda forger: forger.secret, self.allowed_forger_propositions))
                         )


    def tryMakeForgerStake(self, sc_node, owner_address, blockSignPubKey, vrf_public_key, amount):
        # a transaction with a forger stake info not compliant with the closed forger list will be successfully
        # included in a block but the receipt will then report a 'failed' status.
        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node, owner_address, blockSignPubKey, vrf_public_key, amount)

        assert_true("result" in makeForgerStakeJsonRes)
        logging.info(json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # check we had a failure in the receipt
        tx_hash = makeForgerStakeJsonRes['result']["transactionId"]
        logging.info("Getting receipt for txhash={}".format(tx_hash))

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))


        status = int(receipt['result']['status'], 16)
        # status == 1 is succesful
        return status == 1




    def tryOpenForgerList(self, sc_node, forgerIndex, api_error_expected=False, forger_node=None):
        try:
            jsonRes = open_forger_list(sc_node, forgerIndex, api_error_expected)
        except Exception as e:
            if api_error_expected:
                logging.info("Api failed as expected: {}".format(str(e.error)))
                return False
            else:
                raise Exception(e)

        if api_error_expected:
          assert_true("error" in jsonRes)
          logging.info("Api failed as expected")
          return False

        if forger_node is None:
            generate_next_block(sc_node, "first node")
        else:
            generate_next_block(forger_node, "first node")

        self.sc_sync_all()

        # check we had a failure in the receipt
        tx_hash = jsonRes["transactionId"]
        logging.info("Getting receipt for txhash={}".format(tx_hash))

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))

        status = int(receipt['result']['status'], 16)
        # status == 1 is succesful

        if (status == 1): # Check the logs
            assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
            event = receipt['result']['logs'][0]
            check_open_forger_list_event(event, forgerIndex)

        return status == 1

    def run_test(self):
        ft_amount_in_zen = Decimal('500.0')
        ft_amount_in_wei = convertZenToWei(ft_amount_in_zen)

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        mc_node = self.nodes[0]

        evm_address_sc_node_1 = remove_0x_prefix(self.evm_address)

        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(1, len(stakeList))

        # check we have the expected content of the close forger list
        allowedForgerList = sc_node_1.transaction_allowedForgerList()["result"]
        assert_equal(3, len(allowedForgerList['allowedForgers']))
        assert_equal(0, allowedForgerList['allowedForgers'][0]['openForgersVote'])
        assert_equal(0, allowedForgerList['allowedForgers'][1]['openForgersVote'])
        assert_equal(0, allowedForgerList['allowedForgers'][2]['openForgersVote'])

        # transfer a small fund from MC to SC2 at a new evm address, do not mine mc block
        # this is for enabling SC 2 gas fee payment when sending txes
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        pprint.pprint(evm_address_sc_node_2)

        ft_amount_in_zen_2 = Decimal('1.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen_2,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        time.sleep(2)  # MC needs this

        # Generate SC block and check that FT appears in SCs node wallet
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        initial_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(ft_amount_in_wei, initial_balance)

        initial_balance2 = http_wallet_balance(sc_node_2, evm_address_sc_node_2)
        pprint.pprint(initial_balance2)


        # generate publick keys not contained in the closed forger list
        outlaw_blockSignPubKey = sc_node_1.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        outlaw_vrfPubKey = sc_node_1.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        # Try to stake to an invalid blockSignProposition
        logging.info("Try to stake to an invalid blockSignProposition...")
        result = self.tryMakeForgerStake(
            sc_node_1, evm_address_sc_node_1, outlaw_blockSignPubKey,
            self.allowed_forger_vrf_public_keys[0].publicKey, amount=33)
        assert_false(result)

        # Try to stake to an invalid vrfPublicKey
        logging.info("Try to stake to an invalid vrfPublicKey...")
        result = self.tryMakeForgerStake(
            sc_node_1, evm_address_sc_node_1, self.allowed_forger_propositions[0].publicKey,
            outlaw_vrfPubKey, amount=33)
        assert_false(result)

        # Try to stake with an invalid blockSignProposition and an invalid vrfPublicKey
        logging.info("Try to stake to an invalid blockSignProposition and an invalid vrfPublicKey...")
        result = self.tryMakeForgerStake(
            sc_node_1, evm_address_sc_node_1, outlaw_blockSignPubKey,
            outlaw_vrfPubKey, amount=33)
        assert_false(result)

        # Try to stake with a valid blockSignProposition and valid vrfPublicKey
        logging.info("Try to stake to a valid blockSignProposition and valid vrfPublicKey...")
        result = self.tryMakeForgerStake(
            sc_node_1, evm_address_sc_node_1, self.allowed_forger_propositions[0].publicKey,
            self.allowed_forger_vrf_public_keys[0].publicKey, amount=33)
        assert_true(result)

        #Forger 0 opens the stake
        logging.info("Forger 0 opens the stake")
        result = self.tryOpenForgerList(sc_node_1, forgerIndex=0)
        assert_true(result)
        logging.info("Ok!")

        allowedForgerList = sc_node_1.transaction_allowedForgerList()["result"]
        assert_equal(1, allowedForgerList['allowedForgers'][0]['openForgersVote'])
        assert_equal(0, allowedForgerList['allowedForgers'][1]['openForgersVote'])
        assert_equal(0, allowedForgerList['allowedForgers'][2]['openForgersVote'])

        # Try to stake to an invalid blockSignProposition, it should fail because the list is still closed
        logging.info("Try to stake to an invalid blockSignProposition...")
        result = self.tryMakeForgerStake(
            sc_node_1, evm_address_sc_node_1, outlaw_blockSignPubKey,
            self.allowed_forger_vrf_public_keys[0].publicKey, amount=33)
        assert_false(result)
        self.sc_sync_all()


        #Negative test: Forger 0 tries to open the list with the same index already used
        logging.info("Forger 0 opens the stake")
        result = self.tryOpenForgerList(sc_node_1, forgerIndex=0)
        assert_false(result)
        logging.info("Ok!")


        #Negative test: SC 2 as a forger tries to open the list, should fail because it is not the owner of the
        # signer proposition
        logging.info("SC 2 Forger try to open the stake")
        result = self.tryOpenForgerList(sc_node_2, forgerIndex=1, api_error_expected=True, forger_node=sc_node_1)
        assert_false(result)
        logging.info("Ok!")

        # Negative test: try to open the stake using a negative forgerIndex, the cmd is rejected
        logging.info("Forger 1 tries to open the stake with a negative index")
        result = self.tryOpenForgerList(sc_node_1, forgerIndex=-1, api_error_expected=True)
        assert_false(result)
        logging.info("Ok!")

        #Forger 1 opens the stake thus reaching the majority of 2 out of 3
        logging.info("Forger 1 opens the stake")
        result = self.tryOpenForgerList(sc_node_1, forgerIndex=1)
        assert_true(result)
        logging.info("Ok!")

        allowedForgerList = sc_node_1.transaction_allowedForgerList()["result"]
        assert_equal(1, allowedForgerList['allowedForgers'][0]['openForgersVote'])
        assert_equal(1, allowedForgerList['allowedForgers'][1]['openForgersVote'])
        assert_equal(0, allowedForgerList['allowedForgers'][2]['openForgersVote'])

        # Try to stake to a blockSignProposition not in the allowed forger list. This time must succeed since we just
        # open the forger list
        logging.info("Try to stake to a blockSignProposition not in the allowed forder list...")
        result = self.tryMakeForgerStake(
            sc_node_1, evm_address_sc_node_1, outlaw_blockSignPubKey,
            self.allowed_forger_vrf_public_keys[0].publicKey, amount=33)
        assert_true(result)

        #Forger 2 tries to open the stake beyond the majority of 2 out of 3
        logging.info("Forger 2 opens the stake")
        result = self.tryOpenForgerList(sc_node_1, forgerIndex=2)
        assert_false(result)
        logging.info("Ok!")

        allowedForgerList = sc_node_1.transaction_allowedForgerList()["result"]
        assert_equal(1, allowedForgerList['allowedForgers'][0]['openForgersVote'])
        assert_equal(1, allowedForgerList['allowedForgers'][1]['openForgersVote'])
        assert_equal(0, allowedForgerList['allowedForgers'][2]['openForgersVote'])



if __name__ == "__main__":
    SCEvmClosedForgerList().main()
