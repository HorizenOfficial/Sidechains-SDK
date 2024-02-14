#!/usr/bin/env python3
import json
import logging
import os
import re

from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake, deploy_smart_contract
from SidechainTestFramework.account.utils import convertZenToZennies, computeForgedTxFee
from SidechainTestFramework.sc_forging_util import check_mcreference_presence
from SidechainTestFramework.scutil import (
    connect_sc_nodes, generate_next_block, SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME,
    start_sc_node, stop_sc_node, wait_for_sc_node_initialization,
    EVM_APP_BINARY, )
from httpCalls.block.getFeePayments import http_block_getFeePayments
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain, assert_false, )

"""
Check Forger fee payments to a reward address that is a Smart Contract:
1. Forge block to reach fee distribution epoch.
Configuration:
    Start 1 MC node and 2 SC node.
    SC nodes are connected to the MC node.
Test:
    - Start 2 SC nodes
    - deploy a smart contract on SC node 1
    - restart sc node 2 with a new forger reward address
    - create forger stake on sc node 2
    - forge block to reach fee distribution epoch
    - verify that smart contract balance is updated with the forging fee
    
    This test doesn't support --allforks.
"""


class ScEvmForgingFeePayments(AccountChainSetup):
    FORGER_REWARD_ADDRESS = '0000000000000000000012341234123412341234'

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=20, forward_amount=20,
                         block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 100)

    def advance_to_epoch(self, epoch_number: int):
        sc_node = self.sc_nodes[0]
        forging_info = sc_node.block_forgingInfo()
        current_epoch = forging_info["result"]["bestBlockEpochNumber"]
        # make sure we are not already passed the desired epoch
        assert_false(current_epoch > epoch_number, "unexpected epoch number")
        while current_epoch < epoch_number:
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()
            forging_info = sc_node.block_forgingInfo()
            current_epoch = forging_info["result"]["bestBlockEpochNumber"]

    def run_test(self):
        self.sc_ac_setup()
        if self.options.all_forks:
            logging.info("This test cannot be executed with --allforks")
            exit()

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        self.advance_to_epoch(35)

        smart_contract_name = 'ReceivingEther'
        smart_contract = SmartContract(smart_contract_name)
        smart_contract_address = deploy_smart_contract(sc_node_1, smart_contract, self.evm_address)
        FORGER_REWARD_ADDRESS = smart_contract_address

        stop_sc_node(sc_node_2, 1)
        datadir = os.path.join(self.options.tmpdir, "sc_node" + str(1))
        cfgFileName = datadir + ('/node%s.conf' % 1)

        with open(cfgFileName, "r") as config:
            configLines = config.readlines()
        with open(cfgFileName, "w") as sources:
            for line in configLines:
                sources.write(re.sub('forgerRewardAddress = ""',
                                     "forgerRewardAddress = \"" + remove_0x_prefix(FORGER_REWARD_ADDRESS) + "\"", line))

        sc_node_2 = start_sc_node(1, self.options.tmpdir, binary=EVM_APP_BINARY, auth_api_key='Horizen')
        wait_for_sc_node_initialization(self.sc_nodes)
        connect_sc_nodes(self.sc_nodes[0], 1)

        # Do FT of some Zen to SC Node 2
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        ft_amount_in_zen = 2.0
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)
        self.sync_all()
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")

        # Generate MC block and SC block
        mcblock_hash1 = mc_node.generate(1)[0]
        scblock_id1 = generate_next_block(sc_node_1, "first node")
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node_1)
        # Update block fees: node 1 generated block with 0 fees.
        self.sc_sync_all()

        # Create forger stake with some Zen for SC node 2
        sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        forger_stake_amount = 1  # Zen
        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_2, evm_address_sc_node_2, sc2_blockSignPubKey,
                                                    sc2_vrfPubKey,
                                                    convertZenToZennies(forger_stake_amount))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        tx_hash_0 = makeForgerStakeJsonRes['result']['transactionId']
        generate_next_block(sc_node_1, "first node")
        transactionFee_0, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node_1, tx_hash_0)

        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
        mc_node.generate(self.withdrawalEpochLength - 2)
        last_block_id = generate_next_block(sc_node_2, "second node")
        self.sc_sync_all()

        expected_fee = http_block_getFeePayments(sc_node_1, last_block_id)['feePayments'][1]['value']
        actual_fee = smart_contract.static_call(sc_node_1, "getBalance()", fromAddress=self.evm_address,
                                                toAddress=smart_contract_address)[0]

        assert_equal(expected_fee, actual_fee, "Smart Contract received wrong forger fee payment")


if __name__ == "__main__":
    ScEvmForgingFeePayments().main()
