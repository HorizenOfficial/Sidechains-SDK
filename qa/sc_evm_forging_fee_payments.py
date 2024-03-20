#!/usr/bin/env python3
import json
import logging
import math
import time

from eth_utils import add_0x_prefix, function_signature_to_4byte_selector, encode_hex, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake, format_eoa, format_evm, \
    contract_function_static_call, estimate_gas, contract_function_call
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei, convertZenToWei, \
    computeForgedTxFee, FORGER_POOL_RECIPIENT_ADDRESS, VERSION_1_2_FORK_EPOCH, FORGER_STAKE_SMART_CONTRACT_ADDRESS, \
    VERSION_1_3_FORK_EPOCH, VERSION_1_4_FORK_EPOCH
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo, SCForgerConfiguration
from SidechainTestFramework.sc_forging_util import check_mcreference_presence
from SidechainTestFramework.scutil import (
    connect_sc_nodes, generate_account_proposition, generate_next_block, SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME,
    generate_next_blocks, bootstrap_sidechain_nodes, AccountModel, )
from httpCalls.block.getFeePayments import http_block_getFeePayments
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain, assert_false, websocket_port_by_mc_node_index, )

"""
Info about forger account block fee payments
"""


class BlockFeeInfo(object):
    def __init__(self, node, baseFee, forgerTips):
        self.node = node
        self.baseFee = baseFee
        self.forgerTips = forgerTips


"""
Check Forger fee payments:
1. Forging using stakes of different SC nodes
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC nodes are connected to the MC node.
Test:
    - Do FT to the second SC node.
    - Sync SC and MC networks.
    - Delegate coins to forge to the second SC node using coins from the FT.
    - Forge SC block by the first SC node for the next consensus epoch.
    - Forge SC block by the second SC node for the next consensus epoch (Second node ForgingStake must become active).
    - Generate MC and SC blocks to reach the end of the withdrawal epoch. 
    - Check forger payments for the SC nodes.
    
    This test doesn't support --allforks.
"""


class ScEvmForgingFeePayments(AccountChainSetup):
    FORGER_REWARD_ADDRESS = '0000000000000000000012341234123412341234'

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=20, forward_amount=3,
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

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = [
            SCNodeConfiguration(
                MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key='Horizen',
                cert_submitter_enabled=True),

            SCNodeConfiguration(
                MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                forger_options=SCForgerConfiguration(forger_reward_address=self.FORGER_REWARD_ADDRESS),
                api_key='Horizen',
                cert_submitter_enabled=False
            )
        ]

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 3, 20), *sc_node_configuration)
        self.sc_nodes_bootstrap_info = \
            bootstrap_sidechain_nodes(self.options, network,
                                      block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 100,
                                      model=AccountModel)

    def run_test(self):
        if self.options.all_forks:
            logging.info("This test cannot be executed with --allforks")
            exit()

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        self.advance_to_epoch(35)
        sc_block_fee_info = [BlockFeeInfo(1, 0, 0)] * 35

        # Connect and sync SC nodes
        logging.info("Connecting sc nodes...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

        # Do FT of some Zen to SC Node 2
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = 2.0
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

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
        sc_block_fee_info.append(BlockFeeInfo(1, 0, 0))

        self.sc_sync_all()

        # balance is in wei
        initial_balance_2 = http_wallet_balance(sc_node_2, evm_address_sc_node_2)
        assert_equal(ft_amount_in_wei, initial_balance_2)
        logging.info(http_wallet_balance(sc_node_2, evm_address_sc_node_2))
        logging.info(sc_node_2.wallet_getTotalBalance())

        # Create forger stake with some Zen for SC node 2
        sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        forger_stake_amount = 1  # Zen
        forger_stake_amount_in_wei = convertZenToWei(forger_stake_amount)

        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_2, evm_address_sc_node_2, sc2_blockSignPubKey,
                                                    sc2_vrfPubKey,
                                                    convertZenToZennies(forger_stake_amount))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()

        tx_hash_0 = makeForgerStakeJsonRes['result']['transactionId']

        # Generate SC block
        generate_next_block(sc_node_1, "first node")

        transactionFee_0, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node_1, tx_hash_0)

        logging.info(sc_node_2.wallet_getTotalBalance())

        # balance now is initial (ft) minus forgerStake and fee
        assert_equal(
            ft_amount_in_wei -
            (forger_stake_amount_in_wei + transactionFee_0),
            sc_node_2.wallet_getTotalBalance()['result']['balance']
        )

        sc_block_fee_info.append(BlockFeeInfo(1, forgersPoolFee, forgerTip))

        # we now have 2 stakes, one from creation and one just added
        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(2, len(stakeList))

        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        sc_block_fee_info.append(BlockFeeInfo(1, 0, 0))

        self.sc_sync_all()

        recipient_keys = generate_account_proposition("seed3", 1, self.model)[0]
        recipient_proposition = recipient_keys.proposition

        # Create a legacy transaction moving some fund from SC2 address to an external address.
        transferred_amount_in_zen_1 = 0.022
        transferred_amount_in_wei_1 = convertZenToWei(transferred_amount_in_zen_1)

        tx_hash_1 = createLegacyTransaction(sc_node_2,
                      fromAddress=evm_address_sc_node_2,
                      toAddress=recipient_proposition,
                      value=transferred_amount_in_wei_1
        )
        self.sc_sync_all()

        # Generate SC block on SC node 1
        sc_middle_we_block_id = generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        transactionFee_1, forgersPoolFee_1, forgerTip_1 = computeForgedTxFee(sc_node_1, tx_hash_1)
        sc_block_fee_info.append(BlockFeeInfo(1, forgersPoolFee_1, forgerTip_1))

        # Create a eip1559 transaction moving some fund from SC2 address to an external address.
        # This also tests a too high maxPriorityFee value, which should be capped using maxFeePerGas
        transferred_amount_in_zen_2 = 0.001
        transferred_amount_in_wei_2 = convertZenToWei(transferred_amount_in_zen_2)

        blockJson = sc_node_2.rpc_eth_getBlockByHash(add_0x_prefix(sc_middle_we_block_id), False)['result']
        if blockJson is None:
            raise Exception('Unexpected error: block not found {}'.format(sc_middle_we_block_id))
        baseFeePerGas = int(blockJson['baseFeePerGas'], 16)

        tx_hash_2 = createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc_node_2,
                                             toAddress=recipient_proposition,
                                             gasLimit=123400, maxPriorityFeePerGas=56700,
                                             maxFeePerGas=baseFeePerGas + 56690,
                                             value=transferred_amount_in_wei_2)
        self.sc_sync_all()

        # Generate SC block on SC node 2 for the next consensus epoch
        sc_middle_we_block_id = generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        transactionFee_2, forgersPoolFee_2, forgerTip_2 = computeForgedTxFee(sc_node_1, tx_hash_2)
        sc_block_fee_info.append(BlockFeeInfo(2, forgersPoolFee_2, forgerTip_2))

        self.sc_sync_all()

        # let assume a portion of the MC coinbase is sent to the SC as a contribution to the forger pool
        # this funds should not be distributed until fork happens at epoch 60
        ft_pool_amount = 100
        ft_pool_amount_wei = convertZenToWei(ft_pool_amount)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      format_eoa(FORGER_POOL_RECIPIENT_ADDRESS),
                                      ft_pool_amount,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)

        # Generate some MC block to reach the end of the withdrawal epoch
        mc_node.generate(self.withdrawalEpochLength - 2)

        # balance now is initial (ft) without forgerStake and fee and without transferred amounts and fees
        assert_equal(
            ft_amount_in_wei -
            (forger_stake_amount_in_wei + transactionFee_0) -
            (transferred_amount_in_wei_1 + transactionFee_1) -
            (transferred_amount_in_wei_2 + transactionFee_2),
            sc_node_2.wallet_getTotalBalance()['result']['balance']
        )

        # Collect SC node balances before fees redistribution
        sc_node_1_balance_before_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
        sc_node_2_balance_before_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

        # Generate one more block with no fee by SC node 2 to reach the end of the withdrawal epoch
        sc_last_we_block_id = generate_next_block(sc_node_2, "second node")
        sc_block_fee_info.append(BlockFeeInfo(2, 0, 0))

        self.sc_sync_all()

        # assert Forger Pool balance is updated, but not distributed
        forger_pool_balance = int(self.sc_nodes[0].rpc_eth_getBalance(format_evm(FORGER_POOL_RECIPIENT_ADDRESS), 'latest')['result'], 16)
        assert_equal(forger_pool_balance, ft_pool_amount_wei)

        # Collect fee values
        total_fee = 0
        pool_fee = 0.0
        forger_fees = {}
        for sc_block_fee in sc_block_fee_info:
            total_fee += sc_block_fee.baseFee + sc_block_fee.forgerTips
            pool_fee += sc_block_fee.baseFee

        logging.info("total fee = {}".format(total_fee))
        logging.info("pool fee = {}".format(pool_fee))

        average_fee = math.floor(pool_fee / len(sc_block_fee_info))

        for idx, sc_block_fee in enumerate(sc_block_fee_info):
            if sc_block_fee.node in forger_fees:
                forger_fees[sc_block_fee.node] += sc_block_fee.forgerTips
            else:
                forger_fees[sc_block_fee.node] = sc_block_fee.forgerTips

            forger_fees[sc_block_fee.node] += average_fee

            if idx < pool_fee % len(sc_block_fee_info):
                forger_fees[sc_block_fee.node] += 1

            logging.info("block {} fees: {}, {}".format(idx, sc_block_fee.baseFee, sc_block_fee.forgerTips))

        sc_node_1_balance_after_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
        sc_node_2_balance_after_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

        node_1_fees = forger_fees[1]
        node_2_fees = forger_fees[2]

        # Check forger fee payments
        logging.info("SC1 bal before = {}, after = {}".format(sc_node_1_balance_before_payments,
                                                              sc_node_1_balance_after_payments))
        logging.info("SC2 bal before = {}, after = {}".format(sc_node_2_balance_before_payments,
                                                              sc_node_2_balance_after_payments))

        logging.info("SC1 forger fees = {}".format(node_1_fees))
        logging.info("SC2 forger fees = {}".format(node_2_fees))

        assert_equal(node_1_fees + node_2_fees, total_fee)

        # Check forger fee payments
        assert_equal(sc_node_1_balance_before_payments + node_1_fees, sc_node_1_balance_after_payments,
                     "Wrong fee payment amount for SC node 1")
        # SC node 2 is configured to send rewards to some other address
        reward_address_balance = http_wallet_balance(sc_node_2, self.FORGER_REWARD_ADDRESS)
        assert_equal(sc_node_2_balance_before_payments + node_2_fees,
                     sc_node_2_balance_after_payments + reward_address_balance,
                     "Wrong fee payment amount for SC node 2")

        # Check fee payments from API perspective
        # Non-last block of the epoch:
        api_fee_payments_node1 = http_block_getFeePayments(sc_node_1, sc_middle_we_block_id)['feePayments']
        logging.info(api_fee_payments_node1)

        assert_equal(0, len(api_fee_payments_node1),
                     "No fee payments expected to be found for the block in the middle of WE")

        api_fee_payments_node2 = http_block_getFeePayments(sc_node_2, sc_middle_we_block_id)['feePayments']
        logging.info(api_fee_payments_node2)

        assert_equal(0, len(api_fee_payments_node2),
                     "No fee payments expected to be found for the block in the middle of WE")

        # Last block of the epoch:
        api_fee_payments_node1 = http_block_getFeePayments(sc_node_1, sc_last_we_block_id)['feePayments']
        api_fee_payments_node2 = http_block_getFeePayments(sc_node_2, sc_last_we_block_id)['feePayments']
        logging.info(api_fee_payments_node1)
        logging.info(api_fee_payments_node2)

        assert_equal(api_fee_payments_node1, api_fee_payments_node2,
                     "SC nodes have different view on the fee payments")

        for i in range(1, len(forger_fees) + 1):
            assert_equal(forger_fees[i], api_fee_payments_node1[i - 1]['value'],
                         "Different fee value found for payment " + str(i))

        # trigger cert submission
        # Generate 2 SC blocks on SC node and start them automatic cert creation.
        generate_next_block(sc_node_1, "first node")  # 1 SC block to reach the end of WE
        generate_next_block(sc_node_1, "first node")  # 1 SC block to trigger Submitter logic

        # Wait for Certificates appearance
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] < 1 and sc_node_1.submitter_isCertGenerationActive()["result"][
            "state"]:
            logging.info("Wait for certificates in the MC mempool...")
            if sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
                logging.info("sc_node generating certificate now.")
            time.sleep(2)

        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificates was not added to MC node mempool.")

        # Advance to epoch 80 to enable forger pool fork + forger pool distribution cap fork.
        # First block will already be counted for the distribution.
        # Generate more blocks so that in total there were 22 blocks from node_1 and 3 blocks from node_2
        self.advance_to_epoch(VERSION_1_4_FORK_EPOCH)
        generate_next_blocks(sc_node_1, "first node", 1)
        generate_next_blocks(sc_node_2, "second node", 2)

        mc_node.generate(self.withdrawalEpochLength)
        self.sc_sync_all()
        last_block_id = generate_next_block(sc_node_2, "second node")
        self.sc_sync_all()

        # 2500000000 = 12.5 * 10^8 * 20 * 0.1, where
        # 12.5 * 10^8 - base mainchain coinbase reward
        # 20 - withdrawal epoch length
        # 0.1 - divider/coefficient (10% of sum of mainchain coinbase reward for last epoch)
        mc_withdrawal_epoch_distribution_cap = 2500000000
        distribution_cap = convertZenniesToWei(mc_withdrawal_epoch_distribution_cap)
        per_block_fee = distribution_cap // 25
        ft_pool_remaining = ft_pool_amount_wei - distribution_cap
        node_1_fees = per_block_fee * 22
        node_2_fees = per_block_fee * 3

        sc_node_1_balance_before_payments = sc_node_1_balance_after_payments
        sc_node_2_balance_before_payments = sc_node_2_balance_after_payments + reward_address_balance
        sc_node_1_balance_after_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
        sc_node_2_balance_after_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']
        assert_equal(sc_node_1_balance_before_payments + node_1_fees, sc_node_1_balance_after_payments,
                     "Wrong fee payment amount for SC node 1")
        # SC node 2 is configured to send rewards to some other address
        reward_address_balance = http_wallet_balance(sc_node_2, self.FORGER_REWARD_ADDRESS)
        assert_equal(sc_node_2_balance_before_payments + node_2_fees,
                     sc_node_2_balance_after_payments + reward_address_balance,
                     "Wrong fee payment amount for SC node 2")

        # assert forger pool balance is 0 now, as the fees are distributed
        forger_pool_balance = int(self.sc_nodes[0].rpc_eth_getBalance(format_evm(FORGER_POOL_RECIPIENT_ADDRESS), 'latest')['result'], 16)
        assert_equal(ft_pool_remaining, forger_pool_balance)

        fee_payments_api_response = http_block_getFeePayments(sc_node_1, last_block_id)['feePayments']
        assert_equal(node_1_fees, fee_payments_api_response[0]['value'])
        assert_equal(node_2_fees, fee_payments_api_response[1]['value'])

        # reach the VERSION_1_3_FORK_EPOCH fork and upgrade the forger stakes to the new format
        current_best_epoch = sc_node_1.block_forgingInfo()["result"]["bestBlockEpochNumber"]

        for i in range(0, VERSION_1_3_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

        '''
        #####################################################################################
        '''
        native_contract = SmartContract("ForgerStakes")
        method = 'upgrade()'
        # Execute upgrade
        contract_function_call(sc_node_2, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                               evm_address_sc_node_2, method)
        generate_next_block(sc_node_1, "first node")
        '''
        #####################################################################################
        '''

        # we now have 2 stakes, one from creation and one just added
        # res1 = sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": 1, "startPos": 0}))["result"]
        # res2 = sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": 1, "startPos": int(res1['nextPos'])}))["result"]
        #res3 = sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": 100}))["result"]

        # execute native smart contract for getting all associations
        method = 'getPagedForgersStakes(int32,int32)'
        abi_str = function_signature_to_4byte_selector(method)
        start_pos = "00"*32
        size_padded_str = "00"*31 + "01"

        req = {
            "from": format_evm(evm_address_sc_node_2),
            "to": format_evm(FORGER_STAKE_SMART_CONTRACT_ADDRESS),
            "nonce": 3,
            "gasLimit": 2300000,
            "gasPrice": 850000000,
            "value": 0,
            "data": encode_hex(abi_str) + start_pos + size_padded_str
        }
        response = sc_node_1.rpc_eth_call(req, 'latest')
        abi_return_value = remove_0x_prefix(response['result'])
        print(abi_return_value)
        result_string_length = len(abi_return_value)
        # we have an offset of 96 bytes (32 bytes for nextStartPos + 32 bytes for DynamicArray offset + 32 bytes for
        # DynamicArray length) and 1 record with 6 chunks of 32 bytes
        exp_len = 32 + 32 + 32 + 1 * (6 * 32)
        assert_equal(2 * exp_len, result_string_length)


if __name__ == "__main__":
    ScEvmForgingFeePayments().main()
