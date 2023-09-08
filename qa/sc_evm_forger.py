#!/usr/bin/env python3
import json
import logging
import time
from decimal import Decimal

from eth_abi import decode
from eth_utils import add_0x_prefix, encode_hex, event_signature_to_log_topic, remove_0x_prefix, to_hex

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_eoa, format_evm, ac_makeForgerStake, \
    generate_block_and_get_tx_receipt
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.simple_proxy_contract import SimpleProxyContract
from SidechainTestFramework.account.utils import convertZenToWei, \
    convertZenToZennies, convertZenniesToWei, computeForgedTxFee, convertWeiToZen, FORGER_STAKE_SMART_CONTRACT_ADDRESS, \
    WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS
from SidechainTestFramework.scutil import generate_next_block, SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME
from sc_evm_test_contract_contract_deployment_and_interaction import random_byte_string
from test_framework.util import (
    assert_equal, assert_true, fail, forward_transfer_to_sidechain, hex_str_to_bytes, bytes_to_hex_str, assert_false,
)

"""
Configuration: 
    - 2 SC nodes connected with each other
    - 1 MC node
    - SC node 1 owns a stakeAmount made out of cross chain creation output

Test:
    - Check that genesis stake (the one created during sc creation) is what we expect in terms of
      blockSignPublicKey, vrfPublicKey, ownerProposition and amount
    - Send FTs to SC1 (used for forging delegation) and SC2 (used for having some gas)
    - SC2 tries spending the genesis stake which does not own (exception expected)
    - SC1 Delegate 300 Zen and 200 Zen to SC2
    - Check that SC2 can not forge before two epochs are passed by, and afterwards it can
    - SC1 spends the genesis stake
    - SC1 can still forge blocks but after two epochs it can not anymore
    - Test the Forger Staked smart contract can be called by an EVM Smart contract
    - SC1 removes all remaining stakes
    - Verify that it is not possible to forge new SC blocks from the next epoch switch on
    

"""


def get_sc_wallet_pubkeys(sc_node):
    wallet_propositions = sc_node.wallet_allPublicKeys()['result']['propositions']
    # logging.info(wallet_propositions)
    pkey_list = []
    for p in wallet_propositions:
        if 'publicKey' in p:
            pkey_list.append(p['publicKey'])
        elif 'address' in p:
            pkey_list.append(p['address'])

    return pkey_list


def print_current_epoch_and_slot(sc_node):
    ret = sc_node.block_forgingInfo()["result"]
    logging.info("Epoch={}, Slot={}".format(ret['bestBlockEpochNumber'], ret['bestBlockSlotNumber']))


def check_make_forger_stake_event(event, source_addr, owner, amount):
    assert_equal(3, len(event['topics']), "Wrong number of topics in event")
    event_id = remove_0x_prefix(event['topics'][0])
    event_signature = remove_0x_prefix(
        encode_hex(event_signature_to_log_topic('DelegateForgerStake(address,address,bytes32,uint256)')))
    assert_equal(event_signature, event_id, "Wrong event signature in topics")

    from_addr = decode(['address'], hex_str_to_bytes(event['topics'][1][2:]))[0][2:]
    assert_equal(source_addr.lower(), from_addr.lower(), "Wrong from address in topics")

    owner_addr = decode(['address'], hex_str_to_bytes(event['topics'][2][2:]))[0][2:]
    assert_equal(owner, owner_addr, "Wrong owner address in topics")

    (stake_id, value) = decode(['bytes32', 'uint256'], hex_str_to_bytes(event['data'][2:]))
    assert_equal(convertZenToWei(amount), value, "Wrong amount in event")
    return stake_id


def check_spend_forger_stake_event(event, owner, stake_id):
    assert_equal(2, len(event['topics']), "Wrong number of topics in event")
    event_id = remove_0x_prefix(event['topics'][0])
    event_signature = remove_0x_prefix(
        encode_hex(event_signature_to_log_topic('WithdrawForgerStake(address,bytes32)')))
    assert_equal(event_signature, event_id, "Wrong event signature in topics")

    owner_addr = decode(['address'], hex_str_to_bytes(event['topics'][1][2:]))[0][2:]
    assert_equal(owner, owner_addr, "Wrong owner address in topics")

    (stake,) = decode(['bytes32'], hex_str_to_bytes(event['data'][2:]))
    assert_equal(stake_id, to_hex(stake)[2:], "Wrong stake id in data")


class SCEvmForger(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, forward_amount=100,
                         block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 10)

    def run_test(self):

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # blocksign and vrf pub keys are concatenated in custom data param in sc creation (33+32 bytes),
        # get them from mc cmd
        sc_info = mc_node.getscinfo(self.sc_nodes_bootstrap_info.sidechain_id)['items'][0]
        sc_cr_txhash = sc_info['creatingTxHash']
        sc_cr_tx = mc_node.getrawtransaction(str(sc_cr_txhash), 1)

        sc_cr_out_addr_str = sc_cr_tx['vsc_ccout'][0]['address']
        sc_cr_owner_proposition = sc_cr_out_addr_str[24:]

        sc_info_custom_data = sc_info['customData']
        sc_cr_vrf_pub_key = sc_info_custom_data[:66]
        sc_cr_sign_pub_key = sc_info_custom_data[66:]

        # check we have all keys in wallet
        pkey_list = get_sc_wallet_pubkeys(sc_node_1)
        assert_true(sc_cr_owner_proposition in pkey_list, "sc cr owner propostion not in wallet")
        assert_true(sc_cr_vrf_pub_key in pkey_list, "sc cr vrf pub key not in wallet")
        assert_true(sc_cr_sign_pub_key in pkey_list, "sc cr block signer pub key not in wallet")

        # get stake info from genesis block (no owner pub key here)
        sc_genesis_block = sc_node_1.block_best()
        stakeInfo = sc_genesis_block["result"]["block"]["header"]["forgingStakeInfo"]
        stakeAmount = stakeInfo['stakeAmount']
        stakeSignPubKey = stakeInfo["blockSignPublicKey"]["publicKey"]
        stakeVrfPublicKey = stakeInfo["vrfPublicKey"]["publicKey"]

        # check both nodes see the same stake list and same contract amount
        assert_equal(
            sc_node_1.transaction_allForgingStakes()["result"],
            sc_node_2.transaction_allForgingStakes()["result"])
        assert_equal(
            http_wallet_balance(sc_node_1, FORGER_STAKE_SMART_CONTRACT_ADDRESS),
            http_wallet_balance(sc_node_2, FORGER_STAKE_SMART_CONTRACT_ADDRESS))

        # get owner pub key from the node stake list (we have only 1 item)
        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(stakeList), 1)
        stakeOwnerProposition = stakeList[0]['forgerStakeData']["ownerPublicKey"]["address"]

        # check stake info are as expected
        assert_equal(stakeAmount, convertZenToZennies(self.forward_amount), "Forging stake amount is wrong.")
        assert_equal(stakeSignPubKey, sc_cr_sign_pub_key, "Forging stake block sign key is wrong.")
        assert_equal(stakeVrfPublicKey, sc_cr_vrf_pub_key, "Forging stake vrf key is wrong.")
        assert_equal(stakeOwnerProposition, sc_cr_owner_proposition, "Forging stake owner proposition is wrong.")

        # the balance of the smart contract is as expected
        assert_equal(convertZenniesToWei(stakeAmount),
                     http_wallet_balance(sc_node_1, FORGER_STAKE_SMART_CONTRACT_ADDRESS),

                     "Contract address balance is wrong.")

        stake_id_genesis = stakeList[0]['stakeId']

        # transfer a small fund from MC to SC2 at a new evm address, do not mine mc block
        # this is for enabling SC 2 gas fee payment when sending txes
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen_2 = Decimal('1.0')
        ft_amount_in_zennies_2 = convertZenToZennies(ft_amount_in_zen_2)
        ft_amount_in_wei_2 = convertZenniesToWei(ft_amount_in_zennies_2)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen_2,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)

        time.sleep(2)  # MC needs this

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc_node_1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('500.0')
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

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

        # balance is in wei
        initial_balance_2 = http_wallet_balance(sc_node_2, evm_address_sc_node_2)
        assert_equal(ft_amount_in_wei_2, initial_balance_2)

        initial_balance_1 = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(ft_amount_in_wei, initial_balance_1)

        # try spending the stake by a sc node which does not own it
        forg_spend_res_2 = sc_node_2.transaction_spendForgingStake(
            json.dumps({"stakeId": str(stake_id_genesis)}))
        assert_true('error' in forg_spend_res_2, "The command should fail")
        assert_equal(forg_spend_res_2['error']['description'], "Forger Stake Owner not found")

        # Try to delegate stake to a native smart contract. It should fail.
        sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        forgerStake1_amount = 300  # Zen

        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_1, WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS,
                                                    sc2_blockSignPubKey,
                                                    sc2_vrfPubKey, convertZenToZennies(forgerStake1_amount))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake with native smart contract as owner should create a tx: " + json.dumps(
                makeForgerStakeJsonRes))
        else:
            logging.info("Transaction created as expected")
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Checking the receipt
        tx_id = makeForgerStakeJsonRes['result']['transactionId']
        receipt = sc_node_1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_id))
        status = int(receipt['result']['status'], 16)
        assert_equal(0, status, "Make forger stake with native smart contract as owner should create a failed tx")
        # Check the logs
        assert_equal(0, len(receipt['result']['logs']), "Wrong number of events in receipt")

        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, makeForgerStakeJsonRes['result']['transactionId'])
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - gas_fee_paid, account_1_balance)
        initial_balance_1 = account_1_balance

        # Try to delegate stake to a smart contract. It should fail.
        initial_secret = random_byte_string(length=20)
        smart_contract_type = 'TestDeployingContract'
        smart_contract_type = SmartContract(smart_contract_type)

        tx_hash, smart_contract_address = smart_contract_type.deploy(sc_node_1, initial_secret,
                                                                     fromAddress=format_evm(evm_address_sc_node_1),
                                                                     gasLimit=10000000,
                                                                     gasPrice=900000000)
        self.sc_sync_all()
        generate_next_block(sc_node_1, "first node")

        self.sc_sync_all()
        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, tx_hash)
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - gas_fee_paid, account_1_balance)
        initial_balance_1 = account_1_balance

        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_1, format_eoa(smart_contract_address), sc2_blockSignPubKey,
                                                    sc2_vrfPubKey, convertZenToZennies(forgerStake1_amount))

        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake with native smart contract as owner should create a tx: " + json.dumps(
                makeForgerStakeJsonRes))
        else:
            logging.info("Transaction created as expected")
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Checking the receipt
        tx_id = makeForgerStakeJsonRes['result']['transactionId']
        receipt = sc_node_1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_id))
        status = int(receipt['result']['status'], 16)
        assert_equal(0, status, "Make forger stake with native smart contract as owner should create a failed tx")

        # Check the logs
        assert_equal(0, len(receipt['result']['logs']), "Wrong number of events in receipt")

        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, makeForgerStakeJsonRes['result']['transactionId'])
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - gas_fee_paid, account_1_balance)
        initial_balance_1 = account_1_balance

        # SC1 Delegate 300 Zen and 200 Zen to SC node 2 - expected stake is 500 Zen

        forgerStake1_amount = 300  # Zen
        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_1, evm_address_sc_node_1, sc2_blockSignPubKey,
                                                    sc2_vrfPubKey, convertZenToZennies(forgerStake1_amount))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
        tx_hash = makeForgerStakeJsonRes['result']['transactionId']
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
                                      forgerStake1_amount)

        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(2, len(stakeList))

        # reserve a small amount for fee payments
        amount_for_fees_zen = Decimal('0.01')

        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, tx_hash)
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - convertZenToWei(forgerStake1_amount) - gas_fee_paid, account_1_balance)
        initial_balance_1 = account_1_balance

        value_spent = forgerStake1_amount + convertWeiToZen(gas_fee_paid)
        forgerStake2_amount = ft_amount_in_zen - amount_for_fees_zen - Decimal(value_spent)
        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_1, evm_address_sc_node_1, sc2_blockSignPubKey,
                                                    sc2_vrfPubKey, convertZenToZennies(forgerStake2_amount))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()
        tx_hash = makeForgerStakeJsonRes['result']['transactionId']

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
                                      forgerStake2_amount)
        # we now have 3 stakes
        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(3, len(stakeList))

        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, tx_hash)
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - convertZenToWei(forgerStake2_amount) - gas_fee_paid, account_1_balance)
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

        # Generate SC block on SC node forcing epoch change
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # After 2 epoch switches SC2 can now forge
        generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=False)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # check we have the expected stake total amount
        assert_equal(
            convertZenToWei(forgerStake1_amount) +
            convertZenToWei(forgerStake2_amount) +
            convertZenniesToWei(stakeAmount),
            http_wallet_balance(sc_node_1, FORGER_STAKE_SMART_CONTRACT_ADDRESS))

        # spend the genesis stake
        logging.info("SC1 spends genesis stake...")
        spendForgerStakeJsonRes = sc_node_1.transaction_spendForgingStake(
            json.dumps({"stakeId": str(stake_id_genesis)}))
        if "result" not in spendForgerStakeJsonRes:
            fail("spend forger stake failed: " + json.dumps(spendForgerStakeJsonRes))
        else:
            logging.info("Forger stake removed: " + json.dumps(spendForgerStakeJsonRes))
        self.sc_sync_all()
        tx_hash = spendForgerStakeJsonRes['result']['transactionId']

        # Generate SC block on SC node 1 (keep epoch)
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # check the genesis staked amount has been transferred from contract to owner address
        assert_equal(convertZenniesToWei(stakeAmount), http_wallet_balance(sc_node_1, sc_cr_owner_proposition))
        assert_equal(
            convertZenToWei(forgerStake1_amount) +
            convertZenToWei(forgerStake2_amount),
            http_wallet_balance(sc_node_1, FORGER_STAKE_SMART_CONTRACT_ADDRESS))

        # Check balance
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node_1, tx_hash)
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 - gas_fee_paid, account_1_balance)
        initial_balance_1 = account_1_balance

        # Generate SC block on SC node 1 switching epoch
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Generate SC block on SC node 1 keeping epoch
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Verify SC node 1 now can not forge anymore if switching epoch
        try:
            logging.info("SC1 Trying to generate a block: should fail...")
            generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 1.")

        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(2, len(stakeList))

        stakeId_1 = stakeList[0]['stakeId']
        stakeId_2 = stakeList[1]['stakeId']

        # balance is in wei
        final_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1, final_balance)
        initial_balance_1 = final_balance

        bal_sc_cr_prop = http_wallet_balance(sc_node_1, sc_cr_owner_proposition)
        assert_equal(convertZenniesToWei(stakeAmount), bal_sc_cr_prop)
        assert_equal(
            convertZenToWei(forgerStake1_amount) +
            convertZenToWei(forgerStake2_amount),
            http_wallet_balance(sc_node_1, FORGER_STAKE_SMART_CONTRACT_ADDRESS), "Contract address balance is wrong.")


        #######################################################################################################
        # Interoperability test with an EVM smart contract calling forger stakes native contract
        #######################################################################################################

        # Create and deploy evm proxy contract
        # Create a new sc address to be used for the interoperability tests
        evm_address_interop = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        new_ft_amount_in_zen = Decimal('50.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_interop,
                                      new_ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        generate_next_block(sc_node_1, "first node")

        # Deploy proxy contract
        proxy_contract = SimpleProxyContract(sc_node_1, evm_address_interop)

        # Send some funds to the proxy smart contract. Note that nonce=1 because evm_address_interop has deployed the proxy contract.
        contract_funds_in_zen = 10
        createEIP1559Transaction(sc_node_1, fromAddress=evm_address_interop, toAddress=format_eoa(proxy_contract.contract_address),
                                 nonce=1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                 maxFeePerGas=900000000, value=convertZenToWei(contract_funds_in_zen))
        generate_next_block(sc_node_1, "first node")

        native_contract = SmartContract("ForgerStakes")

        # Test getAllForgersStakes()
        method = "getAllForgersStakes()"
        native_input = format_eoa(native_contract.raw_encode_call(method,))

        res = proxy_contract.do_static_call(evm_address_interop, 2, FORGER_STAKE_SMART_CONTRACT_ADDRESS, native_input)

        # res is (bytes32,uint256,address, bytes32,bytes32,bytes1)[]. Its ABI encoding in this case is
        # - first 32 bytes is the offset
        # - second 32 bytes is array length
        # - the remaining are the bytes representing the various (bytes32,uint256,bytes20, bytes32,bytes32,bytes1) tuples.
        # Each tuple is formed of 192 bytes, 32 bytes for each element in the tuple.

        res = res[32:]  # cut offset, don't care in this case
        num_of_stakes = int(bytes_to_hex_str(res[0:32]), 16)
        assert_equal(2, num_of_stakes, "wrong number of forger stakes")
        res = res[32:]  # cut the array length

        elem_size = 192  # 32 * 6
        list_of_elems = [res[i:i + elem_size] for i in range(0, num_of_stakes * elem_size, elem_size)]

        stake_1 = decode(['(bytes32,uint256,address,bytes32,bytes32,bytes1)'], list_of_elems[0])
        stake_2 = decode(['(bytes32,uint256,address,bytes32,bytes32,bytes1)'], list_of_elems[1])

        # Check the stakeId
        assert_equal(stakeList[0]['stakeId'], bytes_to_hex_str(stake_1[0][0]), "wrong stakeId")
        assert_equal(stakeList[1]['stakeId'], bytes_to_hex_str(stake_2[0][0]), "wrong stakeId")
        logging.info("stakeList: {}".format(stakeList))

        # Test forger stake creation: delegate(bytes32,bytes32,bytes1,address)

        method = "delegate(bytes32,bytes32,bytes1,address)"
        vrf_pub_key = hex_str_to_bytes(sc2_vrfPubKey)

        native_input = format_eoa(native_contract.raw_encode_call(method, hex_str_to_bytes(sc2_blockSignPubKey),
                                                                  vrf_pub_key[0:32], vrf_pub_key[32:],
                                                                  evm_address_interop))

        stake_amount_in_zen = 1
        stake_amount_in_wei = convertZenToWei(stake_amount_in_zen)

        # Estimate gas. The result will be compared with the actual used gas
        exp_gas = proxy_contract.estimate_gas(evm_address_interop, 2, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                              stake_amount_in_wei, native_input)

        logging.info("exp_gas: {}".format(exp_gas))

        tx_id = proxy_contract.call_transaction(evm_address_interop, 2, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                                stake_amount_in_wei, native_input)
        receipt = generate_block_and_get_tx_receipt(sc_node_1, tx_id)
        logging.info("receipt: {}".format(receipt))
        logging.info("gas used in receipt: {}".format(receipt['result']['gasUsed']))

        # Check the status of tx
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Wrong tx status in receipt")
        # Check the logs
        assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
        event = receipt['result']['logs'][0]
        stake_id = check_make_forger_stake_event(event, proxy_contract.contract_address[2:], evm_address_interop,
                                                 stake_amount_in_zen)

        # Compare estimated gas with actual used gas. They are not equal because, during the tx execution, more gas than
        # actually needed is removed from the gas pool and then refunded. This causes the gas estimation algorithm to
        # overestimate the gas.
        gas_used = int(receipt['result']['gasUsed'], 16)
        estimated_gas = int(exp_gas['result'], 16)
        assert_true(estimated_gas >= gas_used, "Wrong estimated gas")

        # Check tracer
        trace_response = sc_node_1.rpc_debug_traceTransaction(tx_id, {"tracer": "callTracer"})
        logging.info(trace_response)

        assert_false("error" in trace_response)
        assert_true("result" in trace_response)
        trace_result = trace_response["result"]

        assert_equal(proxy_contract.contract_address.lower(), trace_result["to"].lower())
        assert_equal(1, len(trace_result["calls"]))
        native_call = trace_result["calls"][0]
        assert_equal("CALL", native_call["type"])
        assert_equal(proxy_contract.contract_address.lower(), native_call["from"].lower())
        assert_equal("0x" + FORGER_STAKE_SMART_CONTRACT_ADDRESS, native_call["to"])
        assert_true(int(native_call["gas"], 16) > 0)
        assert_true(int(native_call["gasUsed"], 16) > 0)
        assert_equal("0x" + native_input, native_call["input"])
        assert_equal("0x" + bytes_to_hex_str(stake_id), native_call["output"])
        assert_false("calls" in native_call)

        gas_used_tracer = int(trace_result['gasUsed'], 16)
        # There is a bug so that the gas_used_tracer doesn't have the intrinsic gas (see JIRA 1446)
        assert_true(gas_used >= gas_used_tracer, "Wrong gas")


        # remove the forger stake
        # There is not an easy way to test the 'withdraw' method, so this test is skipped. The problem is that it is difficult
        # to create and sign the message needed for withdrawing a stake, because I don't have a way to sign the message
        # with the private key of the owner. In fact the rpc method eth_sign adds a prefix to the message that is not
        # added by the Forger stake smart contract, so the message verification fails.
        # The same applies to 'openStakeForgerList' method.

        # Remove the stake with API
        spendForgerStakeJsonRes = sc_node_1.transaction_spendForgingStake(
            json.dumps({"stakeId": bytes_to_hex_str(stake_id)}))
        if "result" not in spendForgerStakeJsonRes:
            fail("spend forger stake failed: " + json.dumps(spendForgerStakeJsonRes))
        else:
            logging.info("Forger stake removed: " + json.dumps(spendForgerStakeJsonRes))
        self.sc_sync_all()

        # Generate SC block on SC node 1
        generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        #######################################################################################################
        # End Interoperability test
        #######################################################################################################

        # SC1 remove all the remaining stakes
        spendForgerStakeJsonRes = sc_node_1.transaction_spendForgingStake(
            json.dumps({"stakeId": str(stakeId_1)}))
        if "result" not in spendForgerStakeJsonRes:
            fail("spend forger stake failed: " + json.dumps(spendForgerStakeJsonRes))
        else:
            logging.info("Forger stake removed: " + json.dumps(spendForgerStakeJsonRes))
        self.sc_sync_all()
        tx_hash = spendForgerStakeJsonRes['result']['transactionId']

        # Generate SC block on SC node 1
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
        check_spend_forger_stake_event(event, evm_address_sc_node_1, stakeId_1)

        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(1, len(stakeList))

        # Check balance
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 + convertZenToWei(forgerStake1_amount), account_1_balance)
        initial_balance_1 = account_1_balance

        spendForgerStakeJsonRes = sc_node_1.transaction_spendForgingStake(
            json.dumps({"stakeId": str(stakeId_2)}))
        if "result" not in spendForgerStakeJsonRes:
            fail("spend forger stake failed: " + json.dumps(spendForgerStakeJsonRes))
        else:
            logging.info("Forger stake removed: " + json.dumps(spendForgerStakeJsonRes))
        self.sc_sync_all()
        tx_hash = spendForgerStakeJsonRes['result']['transactionId']

        # Generate SC block on SC node
        generate_next_block(sc_node_2, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # we have no more stakes!!
        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(stakeList), 0)

        # all balance is now at the expected owner address
        # Checking the receipt
        receipt = sc_node_1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status)

        # Check the logs
        assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
        event = receipt['result']['logs'][0]
        check_spend_forger_stake_event(event, evm_address_sc_node_1, stakeId_2)

        # Check balance
        account_1_balance = http_wallet_balance(sc_node_1, evm_address_sc_node_1)
        assert_equal(initial_balance_1 + convertZenToWei(forgerStake2_amount), account_1_balance)

        # Generate SC block on SC node keeping current epoch
        generate_next_block(sc_node_2, "first node", force_switch_to_next_epoch=False)
        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)

        # Try to generate one more SC block switching epoch, that should fail because even if the forging itself could
        # take place (the forger info points to two epoch earlier), the block would not be applied
        # since consensus epoch info are not valid (empty list of stakes)
        try:
            logging.info("Trying to generate a block: should fail...")
            generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
            self.sc_sync_all()
            print_current_epoch_and_slot(sc_node_1)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 1.")

        self.sc_sync_all()
        print_current_epoch_and_slot(sc_node_1)


if __name__ == "__main__":
    SCEvmForger().main()
