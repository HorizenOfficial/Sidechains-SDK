#!/usr/bin/env python3
import json
import logging
import time
from decimal import Decimal

from eth_abi import decode
from eth_utils import add_0x_prefix, encode_hex, event_signature_to_log_topic, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_eoa, ac_makeForgerStake, \
    generate_block_and_get_tx_receipt, contract_function_static_call, contract_function_call, estimate_gas
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.simple_proxy_contract import SimpleProxyContract
from SidechainTestFramework.account.utils import convertZenToWei, \
    convertZenToZennies, computeForgedTxFee, convertWeiToZen, \
    FORGER_STAKE_SMART_CONTRACT_ADDRESS, WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS, VERSION_1_3_FORK_EPOCH
from SidechainTestFramework.scutil import generate_next_block, EVM_APP_SLOT_TIME
from sc_evm_forger import print_current_epoch_and_slot, decode_list_of_forger_stakes, \
    check_make_forger_stake_event, check_spend_forger_stake_event
from test_framework.util import (
    assert_equal, assert_true, fail, forward_transfer_to_sidechain, hex_str_to_bytes, bytes_to_hex_str, )

"""
If it is run with --allforks, all the existing forks are enabled at epoch 2, so it will use Shanghai EVM.
Configuration: 
    - 2 SC nodes connected with each other
    - 1 MC node
    - SC node 1 owns a stakeAmount made out of cross chain creation output

Test:
    - Create some stakes with different owners for node 1 forger before changing storage model 
    - Check that upgrade, stakeOf and getPagedForgersStakesByUser cannot be called before fork 1.3
    - Reach fork point 1.3. Check that stakeOf and getPagedStakesOfUser cannot be called before calling upgrade
    - Execute upgrade and verify that the stakes are the same as before
    - test getPagedStakesOfUser
    - test stakeOf
    - Execute some basic tests just to be sure everything works as before:
        - try spending a stake which does not own (exception expected)
        - Try to delegate stake to a native smart contract. It should fail
        - Try to delegate stake to a smart contract. It should fail.
    - SC1 Delegate 300 Zen and 200 Zen to SC2
    - Check that SC2 can not forge before two epochs are passed by, and afterwards it can
    - SC1 spends all its stake
    - SC1 can still forge blocks but after two epochs it can not anymore
    - Test the getPagedStakesOfUser and stakeOf can be called by an EVM Smart contract
    - removes all remaining stakes
    - Verify that it is not possible to forge new SC blocks from the next epoch switch on
    

"""


def check_list_of_stakes(exp_stake_own_1, list_of_stakes):
    for i in range(len(list_of_stakes)):
        stake = list_of_stakes[i][0]
        assert_equal(exp_stake_own_1[i]['stakeId'], bytes_to_hex_str(stake[0]), "wrong stakeId")
        assert_equal(exp_stake_own_1[i]['forgerStakeData']['stakedAmount'], stake[1], "wrong ownerPublicKey")
        assert_equal(exp_stake_own_1[i]['forgerStakeData']['ownerPublicKey']['address'], stake[2][2:],
                     "wrong ownerPublicKey")
        assert_equal(exp_stake_own_1[i]['forgerStakeData']['forgerPublicKeys']['blockSignPublicKey']['publicKey'],
                     bytes_to_hex_str(stake[3]), "wrong blockSignPublicKey")
        assert_equal(exp_stake_own_1[i]['forgerStakeData']['forgerPublicKeys']['vrfPublicKey']['publicKey'],
                     bytes_to_hex_str(stake[4]) + bytes_to_hex_str(stake[5]), "wrong vrfPublicKey")


def check_upgrade_event(event, old, new):
    assert_equal(1, len(event['topics']), "Wrong number of topics in event")
    event_id = remove_0x_prefix(event['topics'][0])
    event_signature = remove_0x_prefix(
        encode_hex(event_signature_to_log_topic('StakeUpgrade(uint32,uint32)')))
    assert_equal(event_signature, event_id, "Wrong event signature in topics")

    (old_version, new_version) = decode(['uint32', 'uint32'], hex_str_to_bytes(event['data'][2:]))
    assert_equal(old, old_version, "Wrong old version in event")
    assert_equal(new, new_version, "Wrong new version in event")


def decode_paged_list_of_forger_stakes(result, exp_num_of_stakes):
    next_pos = decode(['int32'], result[0:32])[0]
    # next_pos = int(bytes_to_hex_str(result[0:32]), 16)
    res = result[32:]
    list_of_stakes = decode_list_of_forger_stakes(res, exp_num_of_stakes)

    return next_pos, list_of_stakes


class SCEvmForgerV2(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, forward_amount=100,
                         block_timestamp_rewind=1500 * EVM_APP_SLOT_TIME * VERSION_1_3_FORK_EPOCH)

    def run_test(self):

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer a small fund from MC to SC2 at a new evm address, do not mine mc block
        # this is for enabling SC 2 gas fee payment when sending txes
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen_2 = Decimal('500.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen_2,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)

        time.sleep(2)  # MC needs this

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc_node_1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('1000.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        self.sync_all()

        # Generate SC block and check that FTs appears in SCs node wallet
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Get node 1 forger keys
        forger_stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        block_sign_pub_key_1 = forger_stake_list[0]['forgerStakeData']["forgerPublicKeys"]["blockSignPublicKey"][
            "publicKey"]
        vrf_pub_key_1 = forger_stake_list[0]['forgerStakeData']["forgerPublicKeys"]["vrfPublicKey"]["publicKey"]

        # Create forger keys on node 2
        block_sign_pub_key_2 = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        vrf_pub_key_2 = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        # Create some additional addresses, don't care the node
        evm_address_3 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_4 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_5 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        """Create some stakes for node 1 forger:
            - 1 with evm_address_sc_node_1 as owner
            - 3 with evm_address_sc_node_2 as owner
            - 2 with evm_address_3 as owner
            - 1 with evm_address_4 as owner
            - 1 with evm_address_5 as owner
        """
        ac_makeForgerStake(sc_node_1, evm_address_sc_node_1, block_sign_pub_key_1,
                           vrf_pub_key_1, convertZenToZennies(2), 0)
        ac_makeForgerStake(sc_node_1, evm_address_sc_node_2, block_sign_pub_key_1,
                           vrf_pub_key_1, convertZenToZennies(1), 1)
        ac_makeForgerStake(sc_node_1, evm_address_sc_node_2, block_sign_pub_key_1,
                           vrf_pub_key_1, convertZenToZennies(6), 2)
        ac_makeForgerStake(sc_node_1, evm_address_3, block_sign_pub_key_1,
                           vrf_pub_key_1, convertZenToZennies(2), 3)
        ac_makeForgerStake(sc_node_1, evm_address_sc_node_2, block_sign_pub_key_1,
                           vrf_pub_key_1, convertZenToZennies(1), 4)
        ac_makeForgerStake(sc_node_1, evm_address_4, block_sign_pub_key_1,
                           vrf_pub_key_1, convertZenToZennies(3), 5)
        ac_makeForgerStake(sc_node_1, evm_address_3, block_sign_pub_key_1,
                           vrf_pub_key_1, convertZenToZennies(3), 6)
        ac_makeForgerStake(sc_node_1, evm_address_5, block_sign_pub_key_1,
                           vrf_pub_key_1, convertZenToZennies(1), 7)
        self.sc_sync_all()

        # Generate SC block on SC node (keep epoch)
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)
        self.sc_sync_all()

        orig_stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(9, len(orig_stake_list))

        exp_stake_own_1 = []
        exp_stake_own_2 = []
        exp_stake_own_3 = []
        exp_stake_own_4 = []
        exp_stake_own_5 = []
        genesis_stake = None
        for stake in orig_stake_list:
            if stake['forgerStakeData']['ownerPublicKey']['address'] == evm_address_sc_node_1:
                exp_stake_own_1.append(stake)
            elif stake['forgerStakeData']['ownerPublicKey']['address'] == evm_address_sc_node_2:
                exp_stake_own_2.append(stake)
            elif stake['forgerStakeData']['ownerPublicKey']['address'] == evm_address_3:
                exp_stake_own_3.append(stake)
            elif stake['forgerStakeData']['ownerPublicKey']['address'] == evm_address_4:
                exp_stake_own_4.append(stake)
            elif stake['forgerStakeData']['ownerPublicKey']['address'] == evm_address_5:
                exp_stake_own_5.append(stake)
            else:
                genesis_stake = stake

        if self.options.all_forks is False:
            # Check that upgrade, stakeOf and getPagedStakesOfUser cannot be called before fork 1.3

            native_contract = SmartContract("ForgerStakes")
            method = 'getPagedForgersStakesByUser(address,uint32,uint32)'
            try:
                contract_function_static_call(sc_node_1, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                              evm_address_sc_node_1, method, evm_address_sc_node_1, 0, 1)
                fail("getPagedStakesOfUser call should fail before fork point")
            except RuntimeError as err:
                print("Expected exception thrown: {}".format(err))
                assert_true("op code not supported" in str(err))

            method = 'stakeOf(address)'
            try:
                contract_function_static_call(sc_node_1, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                              evm_address_sc_node_1, method, evm_address_sc_node_1)
                fail("stakeOf call should fail before fork point")
            except RuntimeError as err:
                print("Expected exception thrown: {}".format(err))
                assert_true("op code not supported" in str(err))

            method = 'upgrade()'
            try:
                contract_function_static_call(sc_node_1, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                              evm_address_sc_node_1, method)
                fail("upgrade call should fail before fork point")
            except RuntimeError as err:
                print("Expected exception thrown: {}".format(err))
                assert_true("op code not supported" in str(err))

            # Reach fork point 1.3
            current_best_epoch = sc_node_1.block_forgingInfo()["result"]["bestBlockEpochNumber"]
            for i in range(0, VERSION_1_3_FORK_EPOCH - current_best_epoch):
                generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
                self.sc_sync_all()

        # Check that stakeOf and getPagedForgersStakesByUser cannot be called before calling upgrade
        native_contract = SmartContract("ForgerStakes")
        method = 'getPagedForgersStakesByUser(address,uint32,uint32)'
        try:
            contract_function_static_call(sc_node_1, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                          evm_address_sc_node_1, method, evm_address_sc_node_1, 0, 1)
            fail("getPagedForgersStakesByUser call should fail before fork point")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("Forger stake storage not upgraded yet" in str(err))

        method = 'stakeOf(address)'
        try:
            contract_function_static_call(sc_node_1, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                          evm_address_sc_node_1, method, evm_address_sc_node_1)
            fail("stakeOf call should fail before fork point")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("Forger stake storage not upgraded yet" in str(err))

        # Execute upgrade. First try just the static call, to verify that the method's result is correct.
        # Then actually execute the transaction

        method = 'upgrade()'
        res = contract_function_static_call(sc_node_1, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                            evm_address_sc_node_1, method)

        assert_equal(1, res[0], "Storage version should have been version 2")  # Version 2 has value 1

        # Try estimate_gas, it should only charge the intrinsic gas
        native_input = native_contract.raw_encode_call(method)
        result = estimate_gas(sc_node_1,  '0x' + evm_address_sc_node_1, '0x' + FORGER_STAKE_SMART_CONTRACT_ADDRESS, data=native_input)

        intrinsic_gas = 21000 + 4 * 16  # Upgrade signature are 4 non-zero bytes
        assert_equal(intrinsic_gas, int(result["result"], 16))

        # Execute upgrade
        tx_hash = contract_function_call(sc_node_1, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                         evm_address_sc_node_1, method)

        # Check the receipt and the event log
        tx_receipt = generate_block_and_get_tx_receipt(sc_node_1, tx_hash)['result']
        assert_equal('0x1', tx_receipt['status'], 'Transaction failed')
        assert_equal(intrinsic_gas, int(tx_receipt['gasUsed'], 16), "wrong used gas")
        assert_equal(1, len(tx_receipt['logs']), 'Wrong number of logs')

        event = tx_receipt['logs'][0]
        check_upgrade_event(event, 0, 1)

        # Check that the upgrade changed the storage model correctly and that the stakes are correct
        new_stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        new_stake_list.reverse()
        assert_equal(orig_stake_list, new_stake_list)

        # Try getPagedForgersStakesByUser for all users
        method = 'getPagedForgersStakesByUser(address,uint32,uint32)'
        native_input = native_contract.raw_encode_call(method, evm_address_sc_node_1, 0, 1)

        result = estimate_gas(sc_node_1,  '0x' + evm_address_sc_node_1, '0x' + FORGER_STAKE_SMART_CONTRACT_ADDRESS, data=native_input)
        assert_equal(36376, int(result["result"], 16))

        start_pos = 0
        page_size = 2
        # evm_address_sc_node_1 owns just 1 stake
        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_sc_node_1, start_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, len(exp_stake_own_1))
        exp_stake_own_1.reverse()
        check_list_of_stakes(exp_stake_own_1, list_of_stakes)
        assert_equal(-1, next_pos)

        # evm_address_sc_node_2 owns 3 stakes
        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_sc_node_2, start_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, page_size)
        exp_stake_own_2.reverse()
        check_list_of_stakes(exp_stake_own_2[:page_size], list_of_stakes)
        assert_equal(page_size, next_pos)
        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_sc_node_2, next_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, 1)
        check_list_of_stakes(exp_stake_own_2[page_size:], list_of_stakes)
        assert_equal(-1, next_pos)

        # evm_address_3 owns 2 stakes
        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_3, start_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, len(exp_stake_own_3))
        exp_stake_own_3.reverse()
        check_list_of_stakes(exp_stake_own_3, list_of_stakes)
        assert_equal(-1, next_pos)

        # evm_address_4 owns 1 stake
        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_4, start_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, len(exp_stake_own_4))
        exp_stake_own_4.reverse()
        check_list_of_stakes(exp_stake_own_4, list_of_stakes)
        assert_equal(-1, next_pos)

        # evm_address_5 owns 1 stake
        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_5, start_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, len(exp_stake_own_5))
        exp_stake_own_5.reverse()
        check_list_of_stakes(exp_stake_own_5, list_of_stakes)
        assert_equal(-1, next_pos)

        # Try stakeOf

        method = 'stakeOf(address)'
        native_input = native_contract.raw_encode_call(method, evm_address_sc_node_1)

        result = estimate_gas(sc_node_1,  '0x' + evm_address_sc_node_1, '0x' + FORGER_STAKE_SMART_CONTRACT_ADDRESS, data=native_input)
        assert_equal(25608, int(result["result"], 16))

        amount = self.call_stake_of(native_contract, evm_address_sc_node_1, evm_address_sc_node_1)
        exp_total_amount_1 = 0
        for stake in exp_stake_own_1:
            exp_total_amount_1 = exp_total_amount_1 + stake['forgerStakeData']['stakedAmount']
        assert_equal(exp_total_amount_1, amount, "wrong stake amount")

        amount = self.call_stake_of(native_contract, evm_address_sc_node_2, evm_address_sc_node_1)
        exp_total_amount_2 = 0
        for stake in exp_stake_own_2:
            exp_total_amount_2 = exp_total_amount_2 + stake['forgerStakeData']['stakedAmount']
        assert_equal(exp_total_amount_2, amount, "wrong stake amount")

        amount = self.call_stake_of(native_contract, evm_address_3, evm_address_sc_node_1)
        exp_total_amount_3 = 0
        for stake in exp_stake_own_3:
            exp_total_amount_3 = exp_total_amount_3 + stake['forgerStakeData']['stakedAmount']
        assert_equal(exp_total_amount_3, amount, "wrong stake amount")

        amount = self.call_stake_of(native_contract, evm_address_4, evm_address_sc_node_1)
        exp_total_amount_4 = 0
        for stake in exp_stake_own_4:
            exp_total_amount_4 = exp_total_amount_4 + stake['forgerStakeData']['stakedAmount']
        assert_equal(exp_total_amount_4, amount, "wrong stake amount")

        amount = self.call_stake_of(native_contract, evm_address_5, evm_address_sc_node_1)
        exp_total_amount_5 = 0
        for stake in exp_stake_own_5:
            exp_total_amount_5 = exp_total_amount_5 + stake['forgerStakeData']['stakedAmount']
        assert_equal(exp_total_amount_5, amount, "wrong stake amount")

        # Check that adding/removing stakes keep working, as in storage version 1

        # try spending the stake by a sc node which does not own it
        forg_spend_res = sc_node_2.transaction_spendForgingStake(
            json.dumps({"stakeId": str(exp_stake_own_1[0]['stakeId'])}))
        assert_true('error' in forg_spend_res, "The command should fail")
        assert_equal(forg_spend_res['error']['description'], "Forger Stake Owner not found")

        # Try to delegate stake to a native smart contract. It should fail.

        forger_stake1_amount = 300  # Zen
        make_forger_stake_json_res = ac_makeForgerStake(sc_node_1, WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS,
                                                        block_sign_pub_key_2,
                                                        vrf_pub_key_2, convertZenToZennies(forger_stake1_amount))
        if "result" not in make_forger_stake_json_res:
            fail("make forger stake with native smart contract as owner should create a tx: " + json.dumps(
                make_forger_stake_json_res))
        else:
            logging.info("Transaction created as expected")
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Try to delegate stake to a smart contract. It should fail.
        proxy_contract = SimpleProxyContract(sc_node_1, evm_address_sc_node_1, self.options.all_forks)

        self.sc_sync_all()
        generate_next_block(sc_node_1, "first node")

        self.sc_sync_all()
        make_forger_stake_json_res = ac_makeForgerStake(sc_node_1, format_eoa(proxy_contract.contract_address),
                                                        block_sign_pub_key_2,
                                                        vrf_pub_key_2, convertZenToZennies(forger_stake1_amount))

        if "result" not in make_forger_stake_json_res:
            fail("make forger stake with native smart contract as owner should create a tx: " + json.dumps(
                make_forger_stake_json_res))
        else:
            logging.info("Transaction created as expected")

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Checking the receipt
        tx_id = make_forger_stake_json_res['result']['transactionId']
        receipt = sc_node_1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_id))
        status = int(receipt['result']['status'], 16)
        assert_equal(0, status, "Make forger stake with native smart contract as owner should create a failed tx")

        # Check the logs
        assert_equal(0, len(receipt['result']['logs']), "Wrong number of events in receipt")

        # SC1 Delegate 300 Zen and 200 Zen to SC node 2 - expected stake is 500 Zen

        initial_balance_1 = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        forger_stake1_amount = 300  # Zen
        make_forger_stake_json_res = ac_makeForgerStake(sc_node_1, evm_address_sc_node_1, block_sign_pub_key_2,
                                                        vrf_pub_key_2, convertZenToZennies(forger_stake1_amount))
        if "result" not in make_forger_stake_json_res:
            fail("make forger stake failed: " + json.dumps(make_forger_stake_json_res))
        else:
            logging.info("Forger stake created: " + json.dumps(make_forger_stake_json_res))
        tx_hash = make_forger_stake_json_res['result']['transactionId']
        self.sc_sync_all()

        # Generate SC block on SC node (keep epoch)
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Checking the receipt
        receipt = sc_node_1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status)

        # Check the logs
        assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
        event = receipt['result']['logs'][0]
        check_make_forger_stake_event(event, evm_address_sc_node_1, evm_address_sc_node_1,
                                      forger_stake1_amount)

        latest_stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(orig_stake_list) + 1, len(latest_stake_list))

        # reserve a small amount for fee payments
        amount_for_fees_zen = Decimal('0.01')

        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, tx_hash)
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - convertZenToWei(forger_stake1_amount) - gas_fee_paid, account_1_balance)
        initial_balance_1 = account_1_balance

        value_spent = forger_stake1_amount + convertWeiToZen(gas_fee_paid)
        forger_stake2_amount = convertWeiToZen(initial_balance_1)

        forger_stake2_amount = forger_stake2_amount - float(amount_for_fees_zen + Decimal(value_spent))
        make_forger_stake_json_res = ac_makeForgerStake(sc_node_1, evm_address_sc_node_1, block_sign_pub_key_2,
                                                        vrf_pub_key_2, convertZenToZennies(forger_stake2_amount))
        if "result" not in make_forger_stake_json_res:
            fail("make forger stake failed: " + json.dumps(make_forger_stake_json_res))
        else:
            logging.info("Forger stake created: " + json.dumps(make_forger_stake_json_res))
        self.sc_sync_all()
        tx_hash = make_forger_stake_json_res['result']['transactionId']

        # Generate SC block on SC node (keep epoch)
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Checking the receipt
        receipt = sc_node_1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status)

        # Check the logs
        assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
        event = receipt['result']['logs'][0]
        check_make_forger_stake_event(event, evm_address_sc_node_1, evm_address_sc_node_1,
                                      forger_stake2_amount)

        latest_stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(orig_stake_list) + 2, len(latest_stake_list))

        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, tx_hash)
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - convertZenToWei(forger_stake2_amount) - gas_fee_paid, account_1_balance)
        initial_balance_1 = account_1_balance

        # Verify SC node 2 can not forge yet
        try:
            logging.info("SC2 Trying to generate a block: should fail...")
            generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=False)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 2.")
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Generate SC block on SC node forcing epoch change
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Verify SC node 2 can not forge yet
        try:
            logging.info("Trying to generate a block: should fail...")
            generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=False)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 2.")
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Generate SC block on SC node 1 forcing epoch change
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # After 2 epoch switches SC2 can now forge
        generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=False)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # check we have the expected stake total amount
        orig_stake_amount = (exp_total_amount_1 + exp_total_amount_2 + exp_total_amount_3 + exp_total_amount_4 +
                             exp_total_amount_5 + genesis_stake['forgerStakeData']['stakedAmount'])
        assert_equal(
            convertZenToWei(forger_stake1_amount) +
            convertZenToWei(forger_stake2_amount) +
            orig_stake_amount,
            http_wallet_balance(sc_node_1, FORGER_STAKE_SMART_CONTRACT_ADDRESS))
        total_stake = convertZenToWei(forger_stake1_amount) + convertZenToWei(forger_stake2_amount) + orig_stake_amount

        # try getPagedForgersStakesByUser(address,uint32,uint32) again. evm_address_sc_node_1 now owns 3 stakes
        exp_stake_own_1 = []
        for stake in latest_stake_list:
            if stake['forgerStakeData']['ownerPublicKey']['address'] == evm_address_sc_node_1:
                exp_stake_own_1.append(stake)

        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_sc_node_1, start_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, page_size)
        check_list_of_stakes(exp_stake_own_1[:page_size], list_of_stakes)
        assert_equal(page_size, next_pos)
        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_sc_node_1, next_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, 1)
        check_list_of_stakes(exp_stake_own_1[page_size:], list_of_stakes)
        assert_equal(-1, next_pos)

        # try stakeOf again
        amount = self.call_stake_of(native_contract, evm_address_sc_node_1, evm_address_sc_node_1)
        exp_total_amount_1 = exp_total_amount_1 + convertZenToWei(forger_stake1_amount) + convertZenToWei(forger_stake2_amount)
        assert_equal(exp_total_amount_1, amount, "wrong stake amount")

        # spend the genesis stake
        logging.info("SC1 spends genesis stake...")
        spend_forger_stake_json_res = sc_node_1.transaction_spendForgingStake(
            json.dumps({"stakeId": str(genesis_stake['stakeId'])}))
        if "result" not in spend_forger_stake_json_res:
            fail("spend forger stake failed: " + json.dumps(spend_forger_stake_json_res))
        else:
            logging.info("Forger stake removed: " + json.dumps(spend_forger_stake_json_res))
        self.sc_sync_all()
        tx_hash = spend_forger_stake_json_res['result']['transactionId']

        # Generate SC block on SC node 1 (keep epoch)
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # check the genesis staked amount has been transferred from contract to owner address
        stake_amount = genesis_stake['forgerStakeData']['stakedAmount']
        assert_equal(stake_amount, http_wallet_balance(sc_node_1, genesis_stake['forgerStakeData']['ownerPublicKey']['address']))
        expected_total_stake = total_stake - stake_amount
        assert_equal(
            expected_total_stake,
            http_wallet_balance(sc_node_1, FORGER_STAKE_SMART_CONTRACT_ADDRESS))

        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, tx_hash)
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - gas_fee_paid, account_1_balance)

        # Check the new stake list. The latest stake in the old list is moved in the old position of the genesis stake
        current_stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(latest_stake_list) - 1, len(current_stake_list))
        assert_equal(latest_stake_list[:8], current_stake_list[:8])
        assert_equal(latest_stake_list[len(latest_stake_list) - 1], current_stake_list[8])
        assert_equal(latest_stake_list[len(latest_stake_list) - 2], current_stake_list[9])
        latest_stake_list = current_stake_list

        res = self.call_paged_list_of_stakes_of_user(native_contract, genesis_stake['forgerStakeData']['ownerPublicKey']['address'],
                                                     start_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, 0)
        assert_equal(0, len(list_of_stakes))
        assert_equal(-1, next_pos)

        amount = self.call_stake_of(native_contract, genesis_stake['forgerStakeData']['ownerPublicKey']['address'],
                                    evm_address_sc_node_1)
        assert_equal(0, amount)

        # Remove all forger 1 stakes and check that node 1 cannot forge anymore
        # The forger 1's stakes are the first 9
        for i in range(8):
            stake_id = latest_stake_list[i]['stakeId']
            owner = latest_stake_list[i]['forgerStakeData']['ownerPublicKey']['address']
            if owner != evm_address_4 and owner != evm_address_sc_node_2:
                spend_forger_stake_json_res = sc_node_1.transaction_spendForgingStake(
                    json.dumps({"stakeId": str(stake_id)}))
            else:
                spend_forger_stake_json_res = sc_node_2.transaction_spendForgingStake(
                    json.dumps({"stakeId": str(stake_id)}))
            if "result" not in spend_forger_stake_json_res:
                fail("spend forger stake failed: " + json.dumps(spend_forger_stake_json_res))
            else:
                logging.info("Forger stake removed: " + json.dumps(spend_forger_stake_json_res))
            self.sc_sync_all()
            # Generate SC block on SC node 1 (keep epoch)
            generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)

        self.sc_sync_all()

        current_stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(2, len(current_stake_list))
        latest_stake_list = current_stake_list

        # Generate 2 SC block on SC node 2 for switching 2 epochs

        generate_next_block(sc_node_2, "first node", force_switch_to_next_epoch=True)
        generate_next_block(sc_node_2, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # Verify SC node 1 now can not forge anymore if switching epoch
        try:
            logging.info("SC1 Trying to generate a block: should fail...")
            generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 1.")

        #######################################################################################################
        # Interoperability test with an EVM smart contract calling forger stakes native contract
        #######################################################################################################

        method = 'getPagedForgersStakesByUser(address,uint32,uint32)'

        native_input = format_eoa(native_contract.raw_encode_call(method, evm_address_sc_node_1, start_pos, page_size))

        res = proxy_contract.do_static_call(evm_address_sc_node_1, 2, FORGER_STAKE_SMART_CONTRACT_ADDRESS, native_input)

        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, 2)
        assert_equal(evm_address_sc_node_1, list_of_stakes[0][0][2][2:], "wrong ownerPublicKey")
        assert_equal(evm_address_sc_node_1, list_of_stakes[1][0][2][2:], "wrong ownerPublicKey")
        assert_equal(-1, next_pos)

        method = 'stakeOf(address)'
        native_input = format_eoa(native_contract.raw_encode_call(method, evm_address_sc_node_1))

        res = proxy_contract.do_static_call(evm_address_sc_node_1, 2, FORGER_STAKE_SMART_CONTRACT_ADDRESS, native_input)
        amount = int(bytes_to_hex_str(res), 16)

        assert_equal(convertZenToWei(forger_stake1_amount + forger_stake2_amount), amount, "wrong stake amount")

        # remove all the remaining stakes
        spend_forger_stake_json_res = sc_node_1.transaction_spendForgingStake(
            json.dumps({"stakeId": str(latest_stake_list[0]['stakeId'])}))
        if "result" not in spend_forger_stake_json_res:
            fail("spend forger stake failed: " + json.dumps(spend_forger_stake_json_res))
        else:
            logging.info("Forger stake removed: " + json.dumps(spend_forger_stake_json_res))
        self.sc_sync_all()
        tx_hash = spend_forger_stake_json_res['result']['transactionId']

        # Generate SC block on SC node 2
        generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Checking the receipt
        receipt = sc_node_1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status)

        # Check the logs
        assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
        event = receipt['result']['logs'][0]
        check_spend_forger_stake_event(event, evm_address_sc_node_1, latest_stake_list[0]['stakeId'])

        stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(1, len(stake_list))

        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_sc_node_1, start_pos, page_size, evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, 1)
        assert_equal(1, len(list_of_stakes))
        assert_equal(-1, next_pos)

        amount = self.call_stake_of(native_contract, evm_address_sc_node_1, evm_address_sc_node_1)
        assert_equal(convertZenToWei(forger_stake2_amount), amount)

        spend_forger_stake_json_res = sc_node_1.transaction_spendForgingStake(
            json.dumps({"stakeId": str(latest_stake_list[1]['stakeId'])}))
        if "result" not in spend_forger_stake_json_res:
            fail("spend forger stake failed: " + json.dumps(spend_forger_stake_json_res))
        else:
            logging.info("Forger stake removed: " + json.dumps(spend_forger_stake_json_res))
        self.sc_sync_all()

        # Generate SC block on SC node
        generate_next_block(sc_node_2, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # we have no more stakes!!
        stake_list = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(stake_list), 0)

        res = self.call_paged_list_of_stakes_of_user(native_contract, evm_address_sc_node_1, start_pos, page_size,
                                                     evm_address_sc_node_1)
        next_pos, list_of_stakes = decode_paged_list_of_forger_stakes(res, 0)
        assert_equal(0, len(list_of_stakes))
        assert_equal(-1, next_pos)

        amount = self.call_stake_of(native_contract, evm_address_sc_node_1, evm_address_sc_node_1)
        assert_equal(0, amount)

    def call_stake_of(self, native_contract, owner, sender):
        method = 'stakeOf(address)'
        native_input = native_contract.raw_encode_call(method, owner)
        sc_node_1 = self.sc_nodes[0]

        result = sc_node_1.rpc_eth_call(
            {
                "to": "0x" + FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                "from": add_0x_prefix(sender),
                "input": native_input
            }, "latest"
        )

        res = result['result'][2:]
        return int(res, 16)

    def call_paged_list_of_stakes_of_user(self, native_contract, owner, start_pos, page_size, sender):
        method = 'getPagedForgersStakesByUser(address,uint32,uint32)'
        native_input = native_contract.raw_encode_call(method, owner, start_pos, page_size)
        sc_node_1 = self.sc_nodes[0]

        result = sc_node_1.rpc_eth_call(
            {
                "to": "0x" + FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                "from": add_0x_prefix(sender),
                "input": native_input
            }, "latest"
        )

        res = hex_str_to_bytes(result['result'][2:])

        return res


if __name__ == "__main__":
    SCEvmForgerV2().main()
