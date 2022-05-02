#!/usr/bin/env python3
import json

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from httpCalls.block.getFeePayments import http_block_getFeePayments
from test_framework.util import assert_equal, assert_true, websocket_port_by_mc_node_index,\
    forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, generate_next_blocks
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from SidechainTestFramework.websocket_client import WebsocketClient
try:
    import thread
except ImportError:
    import _thread as thread

"""
The purpose of this test is to verify the websocket server inside the SC node for the fee payments related logic.

Network Configuration:
    1 MC nodes, 1 SC node and 1 websocket client

Workflow modelled in this test:
    McNode: send some money to SCNode1 (forward transfer)
    ScNode:
        -Spent some coins and pay fee.
        -Generate MC and SC blocks to reach one block before the fee payments
        -Generate last block of withdrawal epoch
    Test updateTip event:
        -Verify that fee payments were sent
        -Generate 1 more SC block, check that Fee payments are not defined for it.
    Test get single block request:
        -Ask for fee payments block using its hash
        -Ask for fee payments block using its height
"""

class SCWsServerFeePayments(SidechainTestFramework):
    blocks = []
    withdrawal_epoch_length = 10

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=False,
            cert_signing_enabled=False
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, self.withdrawal_epoch_length), sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def run_test(self):
        print("SC ws server fee payments test is starting...")

        epoch_mc_blocks_left = self.withdrawal_epoch_length - 1

        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        public_key1 = self.sc_nodes_bootstrap_info.genesis_account.publicKey
        self.sc_sync_all()

        # We need some free coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      public_key1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress())
        self.sc_sync_all()
        self.blocks.append(generate_next_blocks(sc_node, "", 1)[0])
        self.sc_sync_all()

        epoch_mc_blocks_left -= 1

        # Check that the wallet balance is doubled now (forging stake + the forward transfer)
        assert_equal(http_wallet_balance(sc_node),  (self.sc_nodes_bootstrap_info.genesis_account_balance * 2) * 100000000)

        # Send some coins to ourselves and pay fee
        fee = self.sc_nodes_bootstrap_info.genesis_account_balance / 10
        sendCoinsToAddress(sc_node, public_key1, self.sc_nodes_bootstrap_info.genesis_account_balance - fee, fee)
        assert_equal(1, len(sc_node.transaction_allTransactions()["result"]["transactions"]),
                     "1 Tx expected to be in the SC node mempool.")

        self.blocks.append(generate_next_blocks(sc_node, "", 1)[0])
        assert_equal(0, len(sc_node.transaction_allTransactions()["result"]["transactions"]),
                     "No Txs expected to be in the SC node mempool.")

        # Start websocket client
        ws = WebsocketClient()
        ws_connection = ws.create_connection("ws://localhost:8025/")

        ###########################################################
        #       Check new tip event for fee payments info         #
        ###########################################################
        print("######## Check new tip event for fee payments info test ########")

        # Generate MC blocks to reach the end of the withdrawal epoch
        mc_node.generate(epoch_mc_blocks_left)

        # Generate 1 SC block that should include all pending MC block references and lead to fee payment.
        self.blocks.append(generate_next_blocks(sc_node, "", 1)[0])

        sc_last_we_block_id = self.blocks[-1]
        sc_last_we_block_height = len(self.blocks) + 1  # considering genesis block

        # Check the event type and get the response
        for i in range(0, 2):
            response = json.loads(ws_connection.recv())
            if response['msgType'] == 0 and response['answerType'] == 0: # new Tip event
                new_tip_event = response
            elif response['msgType'] == 0 and response['answerType'] == 2: # changed mempool event
                pass  # skip mempool event
            else:
                raise Exception("Message not expected") # We shouldn't receive any other message

        # After generating a block we expect to receive new tip event
        # Verify static response field
        ws.checkMessageStaticFields(new_tip_event,
                                    ws.EVENT_MSG_TYPE,
                                    -1,
                                    ws.UPDATE_TIP_EVENT)
        event_payload = new_tip_event['eventPayload']

        assert_equal(event_payload['hash'], sc_last_we_block_id)
        assert_equal(event_payload['height'], sc_last_we_block_height)
        assert_true('feePayments' in event_payload)
        api_fee_payments = http_block_getFeePayments(sc_node, sc_last_we_block_id)['feePayments']
        assert_equal(api_fee_payments, event_payload['feePayments']['newBoxes'],
                     "Different fee payments retrieved by new tip event.")

        ###########################################################
        #               Get single block request test             #
        ###########################################################
        print("######## Get single block request test ########")

        # Send get single block request with block hash of the last block of the WE
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_SINGLE_BLOCK_REQUEST,
                                json.dumps({"hash": sc_last_we_block_id})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.GET_SINGLE_BLOCK_REQUEST)
        # Verify responsePayload
        response_payload = response['responsePayload']
        assert_equal(response_payload['hash'], sc_last_we_block_id)
        assert_equal(response_payload['height'], sc_last_we_block_height)
        assert_true('feePayments' in event_payload)
        assert_equal(api_fee_payments, event_payload['feePayments']['newBoxes'],
                     "Different fee payments retrieved for the block.")

        # Send get single block request with block height of the last block of the WE
        response = json.loads(ws.sendMessage(ws_connection,
                               ws.REQUEST_MSG_TYPE,
                               0,
                               ws.GET_SINGLE_BLOCK_REQUEST,
                               json.dumps({"height": sc_last_we_block_height})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                               ws.RESPONSE_MSG_TYPE,
                               0,
                               ws.GET_SINGLE_BLOCK_REQUEST)
        # Verify responsePayload
        response_payload = response['responsePayload']
        assert_equal(response_payload['hash'], sc_last_we_block_id)
        assert_equal(response_payload['height'], sc_last_we_block_height)

        assert_true('feePayments' in event_payload)
        assert_equal(api_fee_payments, event_payload['feePayments']['newBoxes'],
                     "Different fee payments retrieved for the block.")

        ###########################################################
        #       Check new tip event without payments info         #
        ###########################################################
        print("######## Check new tip event without payments info test ########")

        # Generate 1 SC block that should not cause to any fee payments
        self.blocks.append(generate_next_blocks(sc_node, "", 1)[0])

        # Check the event type and get the response
        for i in range(0, 2):
            response = json.loads(ws_connection.recv())
            if response['msgType'] == 0 and response['answerType'] == 0:  # new Tip event
                new_tip_event = response
            elif response['msgType'] == 0 and response['answerType'] == 2:  # changed mempool event
                pass  # skip mempool event
            else:
                raise Exception("Message not expected")  # We shouldn't receive any other message

        # After generating a block we expect to receive new tip event
        # Verify static response field
        ws.checkMessageStaticFields(new_tip_event,
                                    ws.EVENT_MSG_TYPE,
                                    -1,
                                    ws.UPDATE_TIP_EVENT)
        event_payload = new_tip_event['eventPayload']

        assert_equal(event_payload['hash'], self.blocks[-1])
        assert_equal(event_payload['height'], sc_last_we_block_height + 1)
        assert_true('feePayments' not in event_payload)
        api_fee_payments = http_block_getFeePayments(sc_node, self.blocks[-1])['feePayments']
        assert_equal(0, len(api_fee_payments), "No fee payments expected to be paid.")

        ###########################################################
        #  Test single block request for block without payments   #
        ###########################################################

        # SC block before the last one in the WE
        response = json.loads(ws.sendMessage(ws_connection,
                                             ws.REQUEST_MSG_TYPE,
                                             0,
                                             ws.GET_SINGLE_BLOCK_REQUEST,
                                             json.dumps({"hash": self.blocks[-3]})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                    ws.RESPONSE_MSG_TYPE,
                                    0,
                                    ws.GET_SINGLE_BLOCK_REQUEST)
        # Verify responsePayload
        response_payload = response['responsePayload']
        assert_equal(response_payload['hash'], self.blocks[-3])
        assert_equal(response_payload['height'], sc_last_we_block_height - 1)
        assert_true('feePayments' not in event_payload)
        api_fee_payments = http_block_getFeePayments(sc_node, self.blocks[-3])['feePayments']
        assert_equal(0, len(api_fee_payments), "No fee payments expected to be paid.")

        # SC block after the last one of the WE
        response = json.loads(ws.sendMessage(ws_connection,
                                             ws.REQUEST_MSG_TYPE,
                                             0,
                                             ws.GET_SINGLE_BLOCK_REQUEST,
                                             json.dumps({"hash": self.blocks[-1]})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                    ws.RESPONSE_MSG_TYPE,
                                    0,
                                    ws.GET_SINGLE_BLOCK_REQUEST)
        # Verify responsePayload
        response_payload = response['responsePayload']
        assert_equal(response_payload['hash'], self.blocks[-1])
        assert_equal(response_payload['height'], sc_last_we_block_height + 1)
        assert_true('feePayments' not in event_payload)
        api_fee_payments = http_block_getFeePayments(sc_node, self.blocks[-1])['feePayments']
        assert_equal(0, len(api_fee_payments), "No fee payments expected to be paid.")


if __name__ == "__main__":
    SCWsServerFeePayments().main()
