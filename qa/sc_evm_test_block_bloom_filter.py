#!/usr/bin/env python3
import logging
from eth_utils import keccak
from eth_bloom import BloomFilter
from decimal import Decimal

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract, EvmExecutionError
from SidechainTestFramework.account.address_util import format_evm
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, websocket_port_by_mc_node_index, \
    forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    is_mainchain_block_included_in_sc_block, check_mainchain_block_reference_info, AccountModelBlockVersion, \
    EVM_APP_BINARY, generate_next_blocks, generate_next_block, DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND
from SidechainTestFramework.account.evm_util import CallMethod
from SidechainTestFramework.account.eoa_util import eoa_transaction

global_call_method = CallMethod.RPC_EIP155

"""
Test:
    - Deploy the ERC20 smart contract
    - Transfer some tokens from one address to another (emit Transfer event) and mine a new block
    - Check if the Transfer event is present in bloom filter of latest block together with ERC20 contract address, sender and recipient
    - Make an EOA transfer and mine a new block
    - Check if the bloom filter is empty (no SC was called, so nothing was stored in bloom filter)
    - Deploy the ERC721 smart contract
    - Mint tokens to some address and mine a new block
    - Check if Transfer event was emitted together with sender (since it was minted it is address(0)), recipient and token id
"""


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
        gas = smart_contract.estimate_gas(node, method, tokenid, fromAddress=source_account, toAddress=contract_address,
                                          value=amount)
        res = smart_contract.call_function(node, method, tokenid, fromAddress=source_account, gasLimit=gas,
                                           toAddress=contract_address, value=amount, call_method=call_method)
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


def call_addr_uint_fn(node, smart_contract, contract_address, source_addr, addr, uint, static_call, generate_block,
                      method, overrideGas=None):
    if static_call:
        res = smart_contract.static_call(node, method, format_evm(addr), uint,
                                         fromAddress=source_addr, toAddress=contract_address)
    else:
        if overrideGas is None:
            estimated_gas = smart_contract.estimate_gas(node, method, format_evm(addr), uint,
                                                        fromAddress=source_addr, toAddress=format_evm(contract_address))
        res = smart_contract.call_function(node, method, format_evm(addr), uint,
                                           fromAddress=source_addr,
                                           gasLimit=estimated_gas if overrideGas is None else overrideGas,
                                           toAddress=contract_address)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)
    return res


def call_addr_addr_uint_fn(node, smart_contract, contract_address, source_addr, addr1, addr2, uint, static_call,
                           generate_block, method, overrideGas=None):
    if static_call:
        res = smart_contract.static_call(node, method, addr1, addr2, uint,
                                         fromAddress=source_addr, toAddress=contract_address)
    else:
        if overrideGas is None:
            estimated_gas = smart_contract.estimate_gas(node, method, addr1, addr2, uint,
                                                        fromAddress=source_addr, toAddress=contract_address)
        res = smart_contract.call_function(node, method, addr1, addr2, uint,
                                           fromAddress=source_addr,
                                           gasLimit=estimated_gas if overrideGas is None else overrideGas,
                                           toAddress=contract_address)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)
    return res


def transfer_tokens(node, smart_contract, contract_address, source_account, target_account, amount, *,
                    static_call=False, generate_block=True, overrideGas=None):
    method = 'transfer(address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing transfer of ".format(method) +
              "{} tokens from 0x{} to 0x{}".format(amount, source_account, target_account))
    else:
        logging.info("Calling {}: transferring {} tokens from 0x{} to 0x{}".format(method, amount, source_account,
                                                                            target_account))

    return call_addr_uint_fn(node, smart_contract, contract_address, source_account, target_account, amount,
                             static_call, generate_block, method, overrideGas)


def transfer_from_tokens(node, smart_contract, contract_address, tx_sender_account, source_account, target_account,
                         amount, *, static_call=False, generate_block=True):
    method = 'transferFrom(address,address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing transfer of ".format(method) +
              "{} tokens from 0x{} to 0x{}".format(amount, source_account, target_account))
    else:
        logging.info("Calling {}: transferring {} tokens from 0x{} to 0x{}".format(method, amount, source_account,
                                                                            target_account))

    return call_addr_addr_uint_fn(node, smart_contract, contract_address, tx_sender_account, source_account,
                                  target_account, amount, static_call=static_call, generate_block=generate_block,
                                  method=method)


def approve(node, smart_contract, contract_address, source_account, target_account, amount, *, static_call=False,
            generate_block=True):
    method = 'approve(address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing approval of ".format(method) +
              "{} tokens from 0x{} to 0x{}".format(amount, source_account, target_account))
    else:
        logging.info(
            "Calling {}: approving {} tokens from 0x{} to 0x{}".format(method, amount, source_account, target_account))

    return call_addr_uint_fn(node, smart_contract, contract_address, source_account, target_account, amount,
                             static_call, generate_block, method)


def compare_balance(node, smart_contract, contract_address, account_address, expected_balance):
    logging.info("Checking balance of 0x{}...".format(account_address))
    res = smart_contract.static_call(node, 'balanceOf(address)', account_address,
                                     fromAddress=account_address, toAddress=contract_address)
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


def compare_total_supply(node, smart_contract, contract_address, sender_address, expected_supply):
    logging.info("Checking total supply of token at 0x{}...".format(contract_address))
    res = smart_contract.static_call(node, 'totalSupply()', fromAddress=sender_address, toAddress=contract_address)
    logging.info("Expected supply: '{}', actual supply: '{}'".format(expected_supply, res[0]))
    assert_equal(res[0], expected_supply)
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


class SCEvmBlockBloomFilter(SidechainTestFramework):
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
        TOPIC_LENGTH = 64
        EMPTY_BLOOM_FILTER = "0x" + 512 * "0"

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
        evm_address = '0x' + ret["result"]["proposition"]["address"]
        logging.info("pubkey = {}".format(evm_address))

        # call a legacy wallet api
        ret = sc_node.wallet_allPublicKeys()
        logging.info(ret)

        ft_amount_in_zen = Decimal("33.22")
        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      evm_address[2:],
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_best_block = sc_node.block_best()["result"]
        logging.info(sc_best_block)

        smart_contract_type = 'TestERC20'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        erc20_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        erc20_address = deploy_erc20_smart_contract(sc_node, erc20_contract, evm_address)

        res = compare_total_supply(sc_node, erc20_contract, erc20_address, evm_address, initial_balance)
        res = compare_balance(sc_node, erc20_contract, erc20_address, evm_address, initial_balance)

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = ret["result"]["proposition"]["address"]
        transfer_amount = 1

        # Test bloomfilter for ERC20 transfer
        transfer_tokens(sc_node, erc20_contract, erc20_address, evm_address, other_address,
                        transfer_amount, static_call=False, generate_block=True)

        block = sc_node.rpc_eth_getBlockByNumber("latest", "false")
        bloom_filter = BloomFilter(int(block["result"]["logsBloom"], 16))

        assert_true(bytes.fromhex(erc20_address[2:]) in bloom_filter,
                    "bloom filter should contain the address of emitting contract")

        transfer_event_signature = keccak(b"Transfer(address,address,uint256)")

        assert_true(transfer_event_signature in bloom_filter,
                    "bloom filter should contain the signature of transfer event")

        assert_true(bytes.fromhex("0" * (TOPIC_LENGTH - len(other_address)) + other_address) in bloom_filter,
                    "bloom filter should contain the address of receiver")

        evm_address_stripped = evm_address[2:]
        assert_true(
            bytes.fromhex("0" * (TOPIC_LENGTH - len(evm_address_stripped)) + evm_address_stripped) in bloom_filter,
            "bloom filter should contain the address of sender")

        # Test bloomfilter for EOA transfer
        eoa_transfer(sc_node, evm_address, other_address, transfer_amount, static_call=False,
                     generate_block=True)

        block = sc_node.rpc_eth_getBlockByNumber("latest", "false")
        assert_equal(block["result"]["logsBloom"], EMPTY_BLOOM_FILTER, "bloom filter isn't empty")

        # Test bloomfilter for ERC721 transfer
        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        erc721_contract = SmartContract(smart_contract_type)
        erc721_address = deploy_smart_contract(sc_node, erc721_contract, evm_address,
                                               "Test",
                                               "Test", "Test")
        minted_ids_user1 = [1]
        minting_price = 1

        mint_payable(sc_node, erc721_contract, erc721_address, evm_address, minting_price,
                     minted_ids_user1[0], static_call=False, generate_block=True)

        block = sc_node.rpc_eth_getBlockByNumber("latest", "false")
        bloom_filter = BloomFilter(int(block["result"]["logsBloom"], 16))

        assert_true(bytes.fromhex(erc721_address[2:]) in bloom_filter,
                    "bloom filter should contain the address of nft contract")

        transfer_event_signature = keccak(b"Transfer(address,address,uint256)")


        assert_true(transfer_event_signature in bloom_filter,
                    "bloom filter should contain the signature of transfer event")
        assert_true(
            bytes.fromhex("0" * (TOPIC_LENGTH - len(evm_address_stripped)) + evm_address_stripped) in bloom_filter,
            "bloom filter should contain the address of nft receiver")

        assert_true(
            bytes.fromhex(
                "0" * (TOPIC_LENGTH - len(str(minted_ids_user1[0]))) + str(minted_ids_user1[0])) in bloom_filter,
            "bloom filter should contain the nft token id")


if __name__ == "__main__":
    SCEvmBlockBloomFilter().main()
