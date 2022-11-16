#!/usr/bin/env python3
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa, eoa_transaction, CallMethod
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, websocket_port_by_mc_node_index, \
    forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    is_mainchain_block_included_in_sc_block, check_mainchain_block_reference_info, AccountModelBlockVersion, \
    EVM_APP_BINARY, generate_next_blocks, generate_next_block, computeForgedTxFee, \
    DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND

"""
Check basic metamask-like functionality.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Do an EOA to EOA transfer via RPC
        - Test minting of an NFT
        - Check minting results
        - Test transferring of an ERC20
        - Check minting results
"""

# global_call_method = CallMethod.RPC_LEGACY


# global_call_method = CallMethod.RPC_EIP1559


global_call_method = CallMethod.RPC_EIP155


def mint_payable(node, smart_contract, contract_address, source_account, amount, tokenid, *, static_call: bool,
                 generate_block: bool,
                 call_method=global_call_method):
    method = 'mint(uint256)'
        
    if static_call:
        logging.info("Read-only calling {}: testing minting of ".format(method) +
              "a token (id: {}) of collection {} to 0x{}".format(tokenid, contract_address, source_account))
        res = smart_contract.static_call(node, method, tokenid,
                                         fromAddress=source_account,
                                         toAddress=contract_address,
                                         value=amount)
    else:
        logging.info(
            "Calling {}: minting of a token (id: {}) of collection {} to 0x{}".format(method, tokenid, contract_address,
                                                                                      source_account))
        gas = smart_contract.estimate_gas(node, method, tokenid, fromAddress=source_account, toAddress=contract_address, value=amount)
        res = smart_contract.call_function(node, method, tokenid, fromAddress=source_account, gasLimit=gas,
                                           toAddress=contract_address, value=amount, call_method=call_method)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)
    return res


def compare_balance(node, smart_contract, contract_address, account_address, expected_balance):
    logging.info("Checking balance of 0x{}...".format(account_address))
    res = smart_contract.static_call(node, 'balanceOf(address)', account_address,
                                     fromAddress=account_address,
                                     toAddress=contract_address)
    logging.info("Expected balance: '{}', actual balance: '{}'".format(expected_balance, res[0]))
    assert_equal(res[0], expected_balance)
    return res[0]


def compare_allowance(node, smart_contract, contract_address, owner_address, allowed_address, expected_balance):
    logging.info("Checking allowance of 0x{} from 0x{}...".format(allowed_address, owner_address))
    res = smart_contract.static_call(node, 'allowance(address,address)', owner_address, allowed_address,
                                     fromAddress=allowed_address, toAddress=contract_address)
    logging.info("Expected allowance: '{}', actual allowance: '{}'".format(expected_balance, res[0]))
    assert_equal(res[0], expected_balance)
    return res[0]


def call_noarg_fn(node, smart_contract, contract_address, sender_address, static_call, generate_block, method,
                  call_method=global_call_method):
    if static_call:
        res = smart_contract.static_call(node, method, fromAddress=sender_address, toAddress=contract_address)
    else:
        gas = smart_contract.estimate_gas(node, method, fromAddress=sender_address, toAddress=contract_address)
        res = smart_contract.call_function(node, method, fromAddress=sender_address, gasLimit=gas,
                                           toAddress=contract_address, call_method=call_method)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)

    return res


def call_onearg_fn(node, smart_contract, contract_address, sender_address, static_call, generate_block, method, arg,
                   call_method=global_call_method):
    if static_call:
        res = smart_contract.static_call(node, method, arg, fromAddress=sender_address, toAddress=contract_address)
    else:
        gas = smart_contract.estimate_gas(node, method, fromAddress=sender_address, toAddress=contract_address)
        res = smart_contract.call_function(node, method, arg, fromAddress=sender_address, gasLimit=gas,
                                           toAddress=contract_address, call_method=call_method)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)

    return res


def compare_total_supply(node, smart_contract, contract_address, sender_address, expected_supply):
    method = 'totalSupply()'
    logging.info("Checking total supply of token at 0x{}...".format(contract_address))
    res = call_noarg_fn(node, smart_contract, contract_address, sender_address, True, False, method)
    logging.info("Expected supply: '{}', actual supply: '{}'".format(expected_supply, res[0]))
    assert_equal(res[0], expected_supply)
    return res[0]


def compare_name(node, smart_contract, contract_address, sender_address, expected_name):
    method = 'name()'
    logging.info("Checking name of collection at {}...".format(contract_address))
    res = call_noarg_fn(node, smart_contract, contract_address, sender_address, True, False, method)
    logging.info("Expected name: '{}', actual name: '{}'".format(expected_name, res[0]))
    assert_equal(res[0], expected_name)
    return res[0]


def compare_symbol(node, smart_contract, contract_address, sender_address, expected_name):
    method = 'symbol()'
    logging.info("Checking symbol of collection at {}...".format(contract_address))
    res = call_noarg_fn(node, smart_contract, contract_address, sender_address, True, False, method)
    logging.info("Expected symbol: '{}', actual symbol: '{}'".format(expected_name, res[0]))
    assert_equal(res[0], expected_name)
    return res[0]


def compare_ownerof(node, smart_contract, contract_address, sender_address, tokenid, expected_owner):
    method = 'ownerOf(uint256)'
    expected_owner = format_evm(expected_owner)
    logging.info("Checking owner of token {} of collection at {}...".format(tokenid, contract_address))
    res = call_onearg_fn(node, smart_contract, contract_address, sender_address, True, False, method, tokenid)
    logging.info("Expected owner: '{}', actual owner: '{}'".format(expected_owner, res[0]))
    assert_equal(format_evm(res[0]), format_evm(expected_owner))
    return res[0]


def deploy_smart_contract(node, smart_contract, from_address, name, symbol, metadataURI,
                          call_method=global_call_method):

    logging.info("Estimating gas for deployment...")
    estimated_gas = smart_contract.estimate_gas(node, 'constructor', name, symbol, metadataURI,
                                                                fromAddress=from_address)
    logging.info("Estimated gas is {}".format(estimated_gas))
    logging.info("Deploying smart contract...")
    logging.info("From address: 0x{}".format(from_address))
    tx_hash, address = smart_contract.deploy(node, name, symbol, metadataURI,
                                             fromAddress=from_address,
                                             gasLimit=estimated_gas,
                                             call_method=call_method)
    logging.info("Generating next block...")
    generate_next_blocks(node, "first node", 1)
    # TODO check logs when implemented (events)
    tx_receipt = node.rpc_eth_getTransactionReceipt(tx_hash)
    logging.info(tx_receipt)
    assert_equal(tx_receipt['result']['contractAddress'], address.lower())
    address = tx_receipt['result']['contractAddress']
    logging.info("Smart contract deployed successfully to address 0x{}".format(address))
    return address


def get_native_balance(node, addr):
    return int(node.rpc_eth_getBalance(format_evm(addr), "latest")['result'], 16)


def compare_nat_balance(node, addr, expected_balance):
    logging.info("Checking native balance of 0x{}".format(addr))
    new_balance = get_native_balance(node, addr)
    logging.info("Expected native balance: '{}', actual native balance: '{}'".format(expected_balance, new_balance))
    assert_equal(new_balance, expected_balance)
    return new_balance


def set_paused(node, smart_contract, contract_address, sender_address, *, paused: bool, static_call: bool,
               generate_block: bool, call_method=global_call_method):
    if paused:
        method = 'pause()'
    else:
        method = 'unpause()'

    if static_call:
        logging.info("Read-only calling {}: checking (un)pausing of contract at {} from account 0x{}".format(method,
                                                                                                      contract_address,
                                                                                                      sender_address))
    else:
        logging.info("Calling {}: (un)pausing contract at {} from account 0x{}".format(method,
                                                                                contract_address,
                                                                                sender_address))
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)

    return call_noarg_fn(node, smart_contract, contract_address, sender_address, static_call, generate_block, method,
                         call_method=call_method)


def transfer_token(node, smart_contract, contract_address, sender_address, *, token_id: int, from_address=str,
                   target_address=str, static_call: bool, generate_block: bool, call_method=global_call_method):
    method = 'transferFrom(address,address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing transferring of ".format(method) +
              "token (id: {}) from 0x{} to 0x{} via 0x{}".format(token_id, from_address, target_address,
                                                                 sender_address))
        res = smart_contract.static_call(node, method, from_address, target_address, token_id,
                                         fromAddress=sender_address,
                                         toAddress=contract_address)
    else:
        logging.info("Calling {}: transferring".format(method) +
              "token (id: {}) from 0x{} to 0x{} via 0x{}".format(token_id, from_address, target_address,
                                                                 sender_address))

        gas = smart_contract.estimate_gas(node, method, from_address, target_address, token_id, fromAddress=sender_address, 
                                          toAddress=contract_address)
        res = smart_contract.call_function(node, method, from_address,
                                           target_address, token_id, fromAddress=sender_address, gasLimit=gas,
                                           toAddress=contract_address, call_method=call_method)

    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)
    return res


def eoa_transfer(node, sender, receiver, amount, call_method: CallMethod = global_call_method,
                 static_call: bool = False, generate_block: bool = True, tag: str = 'latest'):
    if static_call:
        res = eoa_transaction(node, from_addr=sender, to_addr=receiver, value=amount, static_call=True, tag=tag)
    else:
        res = eoa_transaction(node, from_addr=sender, to_addr=receiver, call_method=call_method, value=amount)
        if generate_block:
            logging.info("generating next block...")
            generate_next_blocks(node, "first node", 1)
    return res


def eoa_assert_native_balance(node, address, expected_balance, tag='latest'):
    res = node.rpc_eth_getBalance(format_evm(address), tag)

    if "result" not in res:
        raise RuntimeError("Something went wrong, see {}".format(str(res)))

    res = res['result']
    balance = int(res[2:], 16)
    assert_equal(expected_balance, balance, "Actual balance did not match expected balance")


def compare_erc20_balance(node, smart_contract, contract_address, account_address, expected_balance):
    logging.info("Checking balance of 0x{}...".format(account_address))
    res = smart_contract.static_call(node, 'balanceOf(address)', account_address,
                                     fromAddress=account_address,
                                     toAddress=contract_address)
    logging.info("Expected balance: '{}', actual balance: '{}'".format(expected_balance, res[0]))
    assert_equal(res[0], expected_balance)
    return res[0]


def transfer_erc20_tokens(node, smart_contract, contract_address, source_account, target_account, amount, *,
                          static_call=False, generate_block=True, estimate_gas: bool = False):
    method = 'transfer(address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing transfer of ".format(method) +
              "{} tokens from 0x{} to 0x{}".format(amount, source_account, target_account))
    else:
        logging.info("Calling {}: transferring {} tokens from 0x{} to 0x{}".format(method, amount, source_account,
                                                                            target_account))

    return call_addr_uint_fn(node, smart_contract, contract_address, source_account, target_account, amount,
                             static_call, generate_block, method)


def call_addr_uint_fn(node, smart_contract, contract_address, source_addr, addr, uint, static_call, generate_block,
                      method):
    if static_call:
        res = smart_contract.static_call(node, method, addr, uint,
                                         fromAddress=source_addr, toAddress=contract_address)
    else:

        gas = smart_contract.estimate_gas(node, method, addr, uint, fromAddress=source_addr, toAddress=contract_address)
        res = smart_contract.call_function(node, method, addr, uint,
                                           fromAddress=source_addr,
                                           gasLimit=gas, toAddress=contract_address)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)
    return res


def compare_erc20_total_supply(node, smart_contract, contract_address, sender_address, expected_supply):
    logging.info("Checking total supply of token at 0x{}...".format(contract_address))
    res = smart_contract.static_call(node, 'totalSupply()', fromAddress=sender_address, toAddress=contract_address)
    logging.info("Expected supply: '{}', actual supply: '{}'".format(expected_supply, res[0]))
    assert_equal(res[0], expected_supply)
    return res[0]


def deploy_erc20_smart_contract(node, smart_contract, from_address):
    logging.info("Deploying smart contract...")
    logging.info("Estimating gas for deployment...")
    estimated_gas = smart_contract.estimate_gas(node, 'constructor',
                                                                fromAddress=from_address)
    logging.info("Estimated gas is {}".format(estimated_gas))
    tx_hash, address = smart_contract.deploy(node,
                                             fromAddress=from_address,
                                             gasLimit=estimated_gas)
    logging.info("Generating next block...")
    generate_next_blocks(node, "first node", 1)
    tx_receipt = node.rpc_eth_getTransactionReceipt(tx_hash)
    assert_equal(format_evm(tx_receipt['result']['contractAddress']), format_evm(address))
    logging.info("Smart contract deployed successfully to address 0x{}".format(address))
    return address


class SCEvmMetamaskTest(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    API_KEY = "Horizen"

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_network(self, split=False):
        self.sc_nodes = self.sc_setup_nodes()
        logging.info("...skip sync since it would timeout as of now")
        # self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            api_key = self.API_KEY
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(num_nodes=1, dirname=self.options.tmpdir,
                              auth_api_key=self.API_KEY,
                              binary=[EVM_APP_BINARY])  # , extra_args=['-agentlib'])

    def run_test(self):
        global global_call_method
        # Setting up
        sc_node = self.sc_nodes[0]
        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = self.nodes[0].getblock(mc_block["hash"], False)
        logging.info("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node.block_best()["result"]

        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        mc_return_address = self.nodes[0].getnewaddress()
        # evm_address = generate_account_proposition("seed2", 1)[0]

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        logging.info(ret)
        evm_address = format_evm(ret["result"]["proposition"]["address"])
        logging.info("pubkey = {}".format(evm_address))

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = format_evm(ret["result"]["proposition"]["address"])

        ft_amount_in_zen = Decimal("33.22")

        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(evm_address),
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        initial_balance = int(sc_node.rpc_eth_getBalance(str(evm_address), "latest")['result'], 16)
        transfer_amount = 1

        # EOA transfers via RPC

        eoa_assert_native_balance(sc_node, evm_address, initial_balance)
        eoa_assert_native_balance(sc_node, other_address, 0)

        eoa_transfer(sc_node, evm_address, other_address, transfer_amount, static_call=True, generate_block=False)
        tx_hash_legacy = eoa_transfer(sc_node, evm_address, other_address, transfer_amount,
                                      call_method=CallMethod.RPC_LEGACY)

        (gas_used_legacy,_,_) = computeForgedTxFee(sc_node, tx_hash_legacy)

        eoa_assert_native_balance(sc_node, evm_address, initial_balance - (transfer_amount + gas_used_legacy))
        eoa_assert_native_balance(sc_node, other_address, transfer_amount)

        eoa_transfer(sc_node, evm_address, other_address, transfer_amount, static_call=True, generate_block=False)
        tx_hash_eip155 = eoa_transfer(sc_node, evm_address, other_address, transfer_amount,
                                      call_method=CallMethod.RPC_EIP155)

        (gas_used_eip155,_,_) = computeForgedTxFee(sc_node, tx_hash_eip155)

        eoa_assert_native_balance(sc_node, evm_address,
                                  initial_balance - (2 * transfer_amount + gas_used_legacy + gas_used_eip155))
        eoa_assert_native_balance(sc_node, other_address, 2 * transfer_amount)

        logging.info(initial_balance - (2 * transfer_amount + gas_used_legacy + gas_used_eip155))

        eoa_transfer(sc_node, evm_address, other_address, transfer_amount, static_call=True, generate_block=False)
        tx_hash_eip1559 = eoa_transfer(sc_node, evm_address, other_address, transfer_amount,
                                       call_method=CallMethod.RPC_EIP1559)

        (gas_used_eip1559,_,_) = computeForgedTxFee(sc_node, tx_hash_eip1559)

        eoa_assert_native_balance(sc_node, other_address, 3 * transfer_amount)
        eoa_assert_native_balance(sc_node, evm_address, initial_balance - (
                3 * transfer_amount + gas_used_legacy + gas_used_eip155 + gas_used_eip1559))

        zero_address = '0x0000000000000000000000000000000000000000'

        collection_name = "Test ERC721 Tokens"
        collection_symbol = "TET"
        collection_uri = "https://localhost:1337"

        global_call_method = CallMethod.RPC_EIP155
        logging.info("Running test with EIP155 calls")

        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, evm_address,
                                                       collection_name,
                                                       collection_symbol, collection_uri)

        # checking initial data
        res = compare_total_supply(sc_node, smart_contract, smart_contract_address, format_evm(evm_address), 0)
        res = compare_name(sc_node, smart_contract, smart_contract_address, format_evm(evm_address), collection_name)
        res = compare_symbol(sc_node, smart_contract, smart_contract_address, format_evm(other_address),
                             collection_symbol)

        # get basic info about account
        last_nat_balance = get_native_balance(sc_node, evm_address)
        last_balance = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address, 0)

        # Minting NFTs via RPC

        minted_ids_user1 = [1]
        minting_price = 1
        minting_amount = 1
        # check execution before submitting proper transaction
        res = mint_payable(sc_node, smart_contract, smart_contract_address, evm_address, minting_price,
                           minted_ids_user1[0], static_call=True, generate_block=False)
        # check tx receipt once possible
        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, evm_address, minting_price,
                               minted_ids_user1[0], static_call=False, generate_block=True)

        res = compare_total_supply(sc_node, smart_contract, smart_contract_address, evm_address, 1)
        res = compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user1[0],
                              evm_address)
        (gas_used,_,_) = computeForgedTxFee(sc_node, tx_hash)
        last_nat_balance = compare_nat_balance(sc_node, evm_address, last_nat_balance - minting_price - gas_used)
        last_balance = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                                       last_balance + minting_amount)

        # Testing ERC20 functionality
        smart_contract_type = 'TestERC20'
        smart_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        smart_contract_address = deploy_erc20_smart_contract(sc_node, smart_contract, evm_address)

        res = compare_erc20_total_supply(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)
        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)

        transfer_amount = 99

        # Testing normal transfer
        check_res = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                          transfer_amount, static_call=True, generate_block=True)

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)

        tx_hash = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                        transfer_amount, static_call=False, generate_block=True)

        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                                    initial_balance - transfer_amount)
        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)

        global_call_method = CallMethod.RPC_LEGACY
        logging.info("Running test with legacy calls")

        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, evm_address,
                                                       collection_name,
                                                       collection_symbol, collection_uri)

        # checking initial data
        res = compare_total_supply(sc_node, smart_contract, smart_contract_address, format_evm(evm_address), 0)
        res = compare_name(sc_node, smart_contract, smart_contract_address, format_evm(evm_address), collection_name)
        res = compare_symbol(sc_node, smart_contract, smart_contract_address, format_evm(other_address),
                             collection_symbol)

        # get basic info about account
        last_nat_balance = get_native_balance(sc_node, evm_address)
        last_balance = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address, 0)

        # Minting NFTs via RPC

        minted_ids_user1 = [1]
        minting_price = 1
        minting_amount = 1
        # check execution before submitting proper transaction
        res = mint_payable(sc_node, smart_contract, smart_contract_address, evm_address, minting_price,
                           minted_ids_user1[0], static_call=True, generate_block=False)
        # check tx receipt once possible
        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, evm_address, minting_price,
                               minted_ids_user1[0], static_call=False, generate_block=True)

        res = compare_total_supply(sc_node, smart_contract, smart_contract_address, evm_address, 1)
        res = compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user1[0],
                              evm_address)

        (gas_used,_,_) = computeForgedTxFee(sc_node, tx_hash)
        last_nat_balance = compare_nat_balance(sc_node, evm_address, last_nat_balance - minting_price - gas_used)
        last_balance = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                                       last_balance + minting_amount)

        # Testing ERC20 functionality
        smart_contract_type = 'TestERC20'
        smart_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        smart_contract_address = deploy_erc20_smart_contract(sc_node, smart_contract, evm_address)

        res = compare_erc20_total_supply(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)
        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)

        transfer_amount = 99

        # Testing normal transfer
        check_res = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                          transfer_amount, static_call=True, generate_block=True)

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)

        tx_hash = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                        transfer_amount, static_call=False, generate_block=True)

        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                                    initial_balance - transfer_amount)
        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)

        global_call_method = CallMethod.RPC_EIP1559
        logging.info("Running test with EIP1559 calls")

        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, evm_address,
                                                       collection_name,
                                                       collection_symbol, collection_uri)

        # checking initial data
        res = compare_total_supply(sc_node, smart_contract, smart_contract_address, format_evm(evm_address), 0)
        res = compare_name(sc_node, smart_contract, smart_contract_address, format_evm(evm_address), collection_name)
        res = compare_symbol(sc_node, smart_contract, smart_contract_address, format_evm(other_address),
                             collection_symbol)

        # get basic info about account
        last_nat_balance = get_native_balance(sc_node, evm_address)
        last_balance = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address, 0)

        # Minting NFTs via RPC

        minted_ids_user1 = [1]
        minting_price = 1
        minting_amount = 1
        # check execution before submitting proper transaction
        res = mint_payable(sc_node, smart_contract, smart_contract_address, evm_address, minting_price,
                           minted_ids_user1[0], static_call=True, generate_block=False)
        # check tx receipt once possible
        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, evm_address, minting_price,
                               minted_ids_user1[0], static_call=False, generate_block=True)

        res = compare_total_supply(sc_node, smart_contract, smart_contract_address, evm_address, 1)
        res = compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user1[0],
                              evm_address)
        (gas_used,_,_) = computeForgedTxFee(sc_node, tx_hash)
        last_nat_balance = compare_nat_balance(sc_node, evm_address, last_nat_balance - minting_price - gas_used)
        last_balance = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                                       last_balance + minting_amount)

        # Testing ERC20 functionality
        smart_contract_type = 'TestERC20'
        smart_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        smart_contract_address = deploy_erc20_smart_contract(sc_node, smart_contract, evm_address)

        res = compare_erc20_total_supply(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)
        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)

        transfer_amount = 99

        # Testing normal transfer
        check_res = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                          transfer_amount, static_call=True, generate_block=True)

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)

        tx_hash = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                        transfer_amount, static_call=False, generate_block=True)

        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                                    initial_balance - transfer_amount)
        res = compare_erc20_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)


if __name__ == "__main__":
    SCEvmMetamaskTest().main()
