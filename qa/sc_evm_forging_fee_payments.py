#!/usr/bin/env python3
import pprint


from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    connect_sc_nodes, generate_next_block, AccountModelBlockVersion, \
    EVM_APP_BINARY, convertZenToZennies, convertZenniesToWei, get_account_balance, generate_account_proposition, \
    convertZenToWei
from SidechainTestFramework.sc_forging_util import *

import math

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
"""


def computeForgedTxFee(sc_node, tx_hash):
    transactionJson = sc_node.rpc_eth_getTransactionByHash(tx_hash)['result']
    if (transactionJson is None):
        raise Exception('Error: Transaction {} not found (not yet forged?)'.format(tx_hash))
    pprint.pprint(transactionJson)

    receiptJson = sc_node.rpc_eth_getTransactionReceipt(tx_hash)['result']
    if (receiptJson is None):
        raise Exception('Unexpected error: Receipt not found for transaction {}'.format(tx_hash))
    pprint.pprint(receiptJson)

    gasUsed = int(receiptJson['gasUsed'], 16)

    if not 'gasPrice' in transactionJson:
      # eip1559 transaction
      block_hash = receiptJson['blockHash']
      blockJson = sc_node.rpc_eth_getBlockByHash(block_hash, False)['result']
      if (blockJson is None):
          raise Exception('Unexpected error: block not found {}'.format(block_hash))
      pprint.pprint(blockJson)

      baseFeePerGas = int(blockJson['baseFeePerGas'], 16)
      maxPriorityFeePerGas = int(transactionJson['maxPriorityFeePerGas'], 16)
    else:
      baseFeePerGas = 0
      maxPriorityFeePerGas = int(transactionJson['gasPrice'], 16)

    totalTxFee = (baseFeePerGas+maxPriorityFeePerGas)*gasUsed
    forgersPoolFee = baseFeePerGas*gasUsed
    forgerTip = maxPriorityFeePerGas*gasUsed
    print("fee= {}".format(totalTxFee))

    return totalTxFee, forgersPoolFee, forgerTip



class ScEvmForgingFeePayments(SidechainTestFramework):
  number_of_mc_nodes = 1
  number_of_sidechain_nodes = 2

  withdrawal_epoch_length = 5

  def setup_chain(self):
      initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

  def setup_network(self, split=False):
      # Setup nodes and connect them
      self.nodes = self.setup_nodes()

  def setup_nodes(self):
      # Start MC node
      return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

  def sc_setup_chain(self):
      # Bootstrap new SC, specify SC nodes connection to MC node
      mc_node_1 = self.nodes[0]
      sc_node_1_configuration = SCNodeConfiguration(
          MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
      )
      sc_node_2_configuration = SCNodeConfiguration(
          MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
      )

      network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 1, self.withdrawal_epoch_length),
                                       sc_node_1_configuration, sc_node_2_configuration)

      # rewind sc genesis block timestamp for 5 consensus epochs
      self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                               block_timestamp_rewind=720 * 120 * 5,
                                                               blockversion=AccountModelBlockVersion)
  def sc_setup_nodes(self):
      # Start 2 SC nodes
      return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir,
                            binary=[EVM_APP_BINARY] * 2)#, extra_args=[['-agentlib'], []])

  def run_test(self):
      mc_node = self.nodes[0]
      sc_node_1 = self.sc_nodes[0]
      sc_node_2 = self.sc_nodes[1]

      # Connect and sync SC nodes
      print("Connecting sc nodes...")
      connect_sc_nodes(self.sc_nodes[0], 1)
      self.sc_sync_all()
      # Set the genesis SC block fee info
      sc_block_fee_info = [BlockFeeInfo(1, 0, 0)]

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
      initial_balance_2 = get_account_balance(sc_node_2, evm_address_sc_node_2)
      assert_equal(ft_amount_in_wei, initial_balance_2)
      pprint.pprint(get_account_balance(sc_node_2, evm_address_sc_node_2))
      pprint.pprint(sc_node_2.wallet_getTotalBalance())

      # Create forger stake with some Zen for SC node 2
      sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
      sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

      forger_stake_amount = 0.015  # Zen
      forger_stake_amount_in_wei = convertZenToWei(forger_stake_amount)

      forgerStakes = {
          "forgerStakeInfo": {
              "ownerAddress": evm_address_sc_node_2,
              "blockSignPublicKey": sc2_blockSignPubKey,
              "vrfPubKey": sc2_vrfPubKey,
              "value": convertZenToZennies(forger_stake_amount)  # in Satoshi
          }
      }
      makeForgerStakeJsonRes = sc_node_2.transaction_makeForgerStake(json.dumps(forgerStakes))
      if "result" not in makeForgerStakeJsonRes:
          fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
      else:
          print("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
      self.sc_sync_all()

      tx_hash = makeForgerStakeJsonRes['result']['transactionId']

      # Generate SC block
      generate_next_block(sc_node_1, "first node")

      transactionFee_1, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node_1, tx_hash)

      print("Fees: {}, {}, {}".format(transactionFee_1, forgersPoolFee, forgerTip))
      pprint.pprint(get_account_balance(sc_node_2, evm_address_sc_node_2))
      pprint.pprint(sc_node_2.wallet_getTotalBalance())

      # balance now is initial (ft) minus forgerStake and fee
      assert_equal(
        sc_node_2.wallet_getTotalBalance()['result']['balance'],
        ft_amount_in_wei -
        (forger_stake_amount_in_wei + transactionFee_1)
      )

      sc_block_fee_info.append(BlockFeeInfo(1, forgersPoolFee, forgerTip))

      # we now have 2 stakes, one from creation and one just added
      stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
      assert_equal(len(stakeList), 2)
      pprint.pprint(stakeList)

      # Generate SC block on SC node 1 for the next consensus epoch
      generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
      sc_block_fee_info.append(BlockFeeInfo(1, 0, 0))

      self.sc_sync_all()

      transferred_amount = 0.022
      transferred_amount_in_zennies = convertZenToZennies(transferred_amount)
      transferred_amount_in_wei = convertZenniesToWei(transferred_amount_in_zennies)

      recipient_keys = generate_account_proposition("seed3", 1)[0]
      recipient_proposition = recipient_keys.proposition
      print("Trying to send {} zen to address {}".format(transferred_amount, recipient_proposition))

      j = {
          "from": evm_address_sc_node_2,
          "to": recipient_proposition,
          "value": transferred_amount_in_zennies
      }
      response = sc_node_2.transaction_sendCoinsToAddress(json.dumps(j))
      print("tx sent:")
      pprint.pprint(response)
      self.sc_sync_all()

      # Generate SC block on SC node 2 for the next consensus epoch
      sc_middle_we_block_id = generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
      self.sc_sync_all()

      tx_hash_2 = response['result']['transactionId']

      transactionFee_2, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node_1, tx_hash_2)
      print("Fees: {}, {}, {}".format(transactionFee_2, forgersPoolFee, forgerTip))
      sc_block_fee_info.append(BlockFeeInfo(2, forgersPoolFee, forgerTip))

      self.sc_sync_all()

      # Generate 3 MC block to reach the end of the withdrawal epoch
      mc_node.generate(3)

      pprint.pprint(sc_node_1.wallet_getTotalBalance())
      pprint.pprint(sc_node_2.wallet_getTotalBalance())

      # balance now is initial (ft) without forgerStake and fee and without transferred amount and fee
      assert_equal(
          sc_node_2.wallet_getTotalBalance()['result']['balance'],
          ft_amount_in_wei -
          (forger_stake_amount_in_wei + transactionFee_1) -
          (transferred_amount_in_wei  + transactionFee_2)
      )

      # Collect SC node balances before fees redistribution
      sc_node_1_balance_before_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
      sc_node_2_balance_before_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

      # Generate one more block with no fee by SC node 2 to reach the end of the withdrawal epoch
      sc_last_we_block_id = generate_next_block(sc_node_2, "second node")
      sc_block_fee_info.append(BlockFeeInfo(2, 0, 0))

      self.sc_sync_all()

      # Collect fee values
      total_fee = 0
      pool_fee = 0.0
      forger_fees = {}
      for sc_block_fee in sc_block_fee_info:
          total_fee += sc_block_fee.baseFee + sc_block_fee.forgerTips
          pool_fee += sc_block_fee.baseFee

      print("total fee = {}".format(total_fee))
      print("pool fee = {}".format(pool_fee))

      for idx, sc_block_fee in enumerate(sc_block_fee_info):
          if sc_block_fee.node in forger_fees:
              forger_fees[sc_block_fee.node] += sc_block_fee.forgerTips
          else:
              forger_fees[sc_block_fee.node] = sc_block_fee.forgerTips

          forger_fees[sc_block_fee.node] += math.floor(pool_fee / len(sc_block_fee_info))

          if idx < pool_fee % len(sc_block_fee_info):
              forger_fees[sc_block_fee.node] += 1



      sc_node_1_balance_after_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
      sc_node_2_balance_after_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

      node_1_fees = forger_fees[1]
      node_2_fees = forger_fees[2]

      # Check forger fee payments
      print("SC1 bal before = {}, after = {}".format(sc_node_1_balance_before_payments, sc_node_1_balance_after_payments))
      print("SC2 bal before = {}, after = {}".format(sc_node_2_balance_before_payments, sc_node_2_balance_after_payments))

      print("SC1 fees = {}".format(node_1_fees))
      print("SC2 fees = {}".format(node_2_fees))

      assert_equal(node_1_fees + node_2_fees, total_fee)

      assert_equal(sc_node_2_balance_after_payments, sc_node_2_balance_before_payments + node_2_fees,
                   "Wrong fee payment amount for SC node 2")

      # Check forger fee payments
      assert_equal(sc_node_1_balance_after_payments, sc_node_1_balance_before_payments + node_1_fees,
                   "Wrong fee payment amount for SC node 1")
      assert_equal(sc_node_2_balance_after_payments, sc_node_2_balance_before_payments + node_2_fees,
                   "Wrong fee payment amount for SC node 2")

      '''
      TODO enable when API will be ready
      # Check fee payments from API perspective
      # Non-last block of the epoch:
      api_fee_payments_node1 = http_block_getFeePayments2(sc_node_1, sc_middle_we_block_id)['feePayments']
      assert_equal(0, len(api_fee_payments_node1),
                   "No fee payments expected to be found for the block in the middle of WE")
      api_fee_payments_node2 = http_block_getFeePayments2(sc_node_2, sc_middle_we_block_id)['feePayments']
      assert_equal(0, len(api_fee_payments_node2),
                   "No fee payments expected to be found for the block in the middle of WE")

      # Last block of the epoch:
      api_fee_payments_node1 = http_block_getFeePayments2(sc_node_1, sc_last_we_block_id)['feePayments']
      api_fee_payments_node2 = http_block_getFeePayments2(sc_node_2, sc_last_we_block_id)['feePayments']
      assert_equal(api_fee_payments_node1, api_fee_payments_node2,
                   "SC nodes have different view on the fee payments")

      for i in range(1, len(forger_fees) + 1):
          assert_equal(forger_fees[i], api_fee_payments_node1[i - 1]['value'],
                       "Different fee value found for payment " + str(i))
      '''


if __name__ == "__main__":
    ScEvmForgingFeePayments().main()