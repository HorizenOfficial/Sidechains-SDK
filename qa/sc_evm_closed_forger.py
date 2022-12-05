#!/usr/bin/env python3
import json
import logging
import pprint
from decimal import Decimal

from eth_utils import add_0x_prefix, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenToWei
from SidechainTestFramework.sc_boostrap_info import SCForgerConfiguration
from SidechainTestFramework.scutil import (generate_next_block, generate_secrets, generate_vrf_secrets,
    SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME,
)
from test_framework.util import (
    assert_equal, assert_false, assert_true, )

"""
Check the EVM bootstrap feature.

Configuration: 
    - 2 SC nodes configured with a closed list of forger, connected with each other
    - 1 MC node

Test:
    - Try to stake money with invalid forger info and verify that we are not allowed to stake
    - Try the same with forger info pubkeys contained in the closed list, should be succesful
    - Open the stake to the world using the openStakeTransaction and verify that a generic proposition (not included in the forger list) 
    is allowed to forge.


"""


class SCEvmClosedForgerList(AccountChainSetup):

    number_of_sidechain_nodes = 1

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
        forgerStakes = {
            "forgerStakeInfo": {
                "ownerAddress": owner_address,  # SC node 1 is an owner
                "blockSignPublicKey": blockSignPubKey,
                "vrfPubKey": vrf_public_key,
                "value": convertZenToZennies(amount)  # in Satoshi
            }
        }
        makeForgerStakeJsonRes = sc_node.transaction_makeForgerStake(json.dumps(forgerStakes))
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

    def run_test(self):
        ft_amount_in_zen = Decimal('500.0')
        ft_amount_in_wei = convertZenToWei(ft_amount_in_zen)

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node_1 = self.sc_nodes[0]

        evm_address_sc_node_1 = remove_0x_prefix(self.evm_address)

        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(stakeList), 1)


        allowedForgerList = sc_node_1.transaction_allowedForgerList()["result"]
        assert_equal(allowedForgerList['allowedForger'][0][0], 0)
        assert_equal(allowedForgerList['allowedForger'][1][0], 0)
        assert_equal(allowedForgerList['allowedForger'][2][0], 0)

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
        j = {
            "forgerIndex": 0,
        }
        request = json.dumps(j)
        response = sc_node_1.transaction_openStakeForgerList(request)
        pprint.pprint(response)

        assert_true("error" not in response)
        self.sc_sync_all()
        logging.info("Ok!")

        # Generate SC block and check that FT appears in SCs node wallet
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        allowedForgerList = sc_node_1.transaction_allowedForgerList()["result"]
        assert_equal(allowedForgerList['allowedForger'][0][0], 1)
        assert_equal(allowedForgerList['allowedForger'][1][0], 0)
        assert_equal(allowedForgerList['allowedForger'][2][0], 0)

        # Try to stake to an invalid blockSignProposition
        logging.info("Try to stake to an invalid blockSignProposition...")
        result = self.tryMakeForgerStake(
            sc_node_1, evm_address_sc_node_1, outlaw_blockSignPubKey,
            self.allowed_forger_vrf_public_keys[0].publicKey, amount=33)
        assert_false(result)
        self.sc_sync_all()

        #Forger 1 opens the stake thus reaching the majority of 2 out of 3
        logging.info("Forger 1 opens the stake")
        j = {
            "forgerIndex": 1,
        }
        request = json.dumps(j)
        response = sc_node_1.transaction_openStakeForgerList(request)
        pprint.pprint(response)

        assert_true("error" not in response)
        self.sc_sync_all()
        logging.info("Ok!")

        # Generate SC block and check that FT appears in SCs node wallet
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        allowedForgerList = sc_node_1.transaction_allowedForgerList()["result"]
        assert_equal(allowedForgerList['allowedForger'][0][0], 1)
        assert_equal(allowedForgerList['allowedForger'][1][0], 1)
        assert_equal(allowedForgerList['allowedForger'][2][0], 0)

        # Try to stake to a blockSignProposition not in the allowed forger list. This time must succeed since we just
        # open the forger list
        logging.info("Try to stake to a blockSignProposition not in the allowed forder list...")
        result = self.tryMakeForgerStake(
            sc_node_1, evm_address_sc_node_1, outlaw_blockSignPubKey,
            self.allowed_forger_vrf_public_keys[0].publicKey, amount=33)
        assert_true(result)


if __name__ == "__main__":
    SCEvmClosedForgerList().main()
