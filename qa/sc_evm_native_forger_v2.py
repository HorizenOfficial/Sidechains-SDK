#!/usr/bin/env python3
import time
from decimal import Decimal

from eth_abi import decode
from eth_utils import encode_hex, event_signature_to_log_topic, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake, \
    generate_block_and_get_tx_receipt, contract_function_static_call, contract_function_call, format_eoa
from SidechainTestFramework.account.utils import convertZenToZennies, FORGER_STAKE_SMART_CONTRACT_ADDRESS, \
    VERSION_1_3_FORK_EPOCH, \
    VERSION_1_4_FORK_EPOCH, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS
from SidechainTestFramework.scutil import generate_next_block, EVM_APP_SLOT_TIME
from sc_evm_forger import print_current_epoch_and_slot, decode_list_of_forger_stakes
from test_framework.util import (
    assert_equal, assert_true, fail, forward_transfer_to_sidechain, bytes_to_hex_str, )

"""
If it is run with --allforks, all the existing forks are enabled at epoch 2, so it will use Shanghai EVM.
Configuration: 
    - 2 SC nodes connected with each other
    - 1 MC node
    - SC node 1 owns a stakeAmount made out of cross chain creation output

Test:
    - Activate Fork 1.3 and execute upgrade, in order to use storage model v2
    - Create some stakes with different owners for node 1 forger using the old native smart contract
    - Check that activate cannot be called on ForgerStake smart contract V2 before fork 1.4
    - Reach fork point 1.4. 
    - Try to execute disable on old Forger stake native contract and verify that it is not possible.
    - Execute activate on ForgerStake smart contract V2 and verify that the total amount of stakes are the same as before
    - Try methods of the old Forger stake native contract and verify they cannot be executed anymore

    

"""


class SCEvmNativeForgerV2(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, forward_amount=100,
                         block_timestamp_rewind=1500 * EVM_APP_SLOT_TIME * VERSION_1_4_FORK_EPOCH)

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

        # Reach fork point 1.3
        current_best_epoch = sc_node_1.block_forgingInfo()["result"]["bestBlockEpochNumber"]
        for i in range(0, VERSION_1_3_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

        old_forger_native_contract = SmartContract("ForgerStakes")
        method = 'upgrade()'
        # Execute upgrade
        tx_hash = contract_function_call(sc_node_1, old_forger_native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                         evm_address_sc_node_1, method)

        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # Check that disable on old smart contract cannot be called before fork 1.4
        method = 'disable()'
        try:
            contract_function_static_call(sc_node_1, old_forger_native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                          evm_address_sc_node_1, method)
            fail("disable call should fail before fork point")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("op code not supported" in str(err))

        # Check that if activate is called before fork 1.4 it doesn't fail, but it is not executed. It is interpreted
        # as an EOA-to-EOA with a data not null.

        forger_v2_native_contract = SmartContract("ForgerStakesV2")
        method = 'activate()'
        tx_hash = contract_function_call(sc_node_1, forger_v2_native_contract, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS,
                                         evm_address_sc_node_1, method, value=convertZenToZennies(2))

        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        tx_receipt = generate_block_and_get_tx_receipt(sc_node_1, tx_hash)['result']
        assert_equal('0x1', tx_receipt['status'], 'Transaction failed')

        # Reach fork point 1.4
        current_best_epoch = sc_node_1.block_forgingInfo()["result"]["bestBlockEpochNumber"]
        for i in range(0, VERSION_1_4_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

        # Check that disable on old smart contract cannot be called from an account that is not FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS
        method = 'disable()'
        try:
            contract_function_static_call(sc_node_1, old_forger_native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                                          evm_address_sc_node_1, method)
            fail("disable call should fail")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("Authorization failed" in str(err))

        # Execute activate.
        method = 'activate()'
        tx_hash = contract_function_call(sc_node_1, forger_v2_native_contract, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS,
                                         evm_address_sc_node_1, method)

        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=False)
        self.sc_sync_all()

        # Check the receipt and the event log
        tx_receipt = generate_block_and_get_tx_receipt(sc_node_1, tx_hash)['result']
        assert_equal('0x1', tx_receipt['status'], 'Transaction failed')
        intrinsic_gas = 21000 + 4 * 16  # activate signature are 4 non-zero bytes
        assert_equal(intrinsic_gas, int(tx_receipt['gasUsed'], 16), "wrong used gas")
        assert_equal(2, len(tx_receipt['logs']), 'Wrong number of logs')

        disable_event = tx_receipt['logs'][0]
        assert_equal(1, len(disable_event['topics']), "Wrong number of topics in disable_event")
        event_id = remove_0x_prefix(disable_event['topics'][0])
        event_signature = remove_0x_prefix(
            encode_hex(event_signature_to_log_topic('DisableStakeV1()')))
        assert_equal(event_signature, event_id, "Wrong event signature in topics")

        activate_event = tx_receipt['logs'][1]
        assert_equal(1, len(activate_event['topics']), "Wrong number of topics in activate_event")
        event_id = remove_0x_prefix(activate_event['topics'][0])
        event_signature = remove_0x_prefix(
            encode_hex(event_signature_to_log_topic('ActivateStakeV2()')))
        assert_equal(event_signature, event_id, "Wrong event signature in topics")

        # TODO here we should check if the stakes are correct. At the moment no method is implemented that allows to
        # retrieve the stakes from the Forger Stake V2

        # Check that activate cannot be called twice

        try:
            tx_hash = contract_function_call(sc_node_1, forger_v2_native_contract,
                                             FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS,
                                             evm_address_sc_node_1, method)
            fail("activate call should fail")
        except RuntimeError as err:
            pass

        # Check that old native smart contract is disabled
        method = "getAllForgersStakes()"
        try:
            old_forger_native_contract.static_call(sc_node_1, method, fromAddress=evm_address_sc_node_1, toAddress= FORGER_STAKE_SMART_CONTRACT_ADDRESS)
            fail("call should fail after activate of Forger Stake v2")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            # error is raised from API since the address has no balance
            assert_true("Method is disabled" in str(err))





if __name__ == "__main__":
    SCEvmNativeForgerV2().main()
