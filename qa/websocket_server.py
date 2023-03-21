#!/usr/bin/env python3
import json
import logging

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from test_framework.util import assert_equal, assert_true, forward_transfer_to_sidechain, \
    websocket_port_by_mc_node_index, assert_false
from SidechainTestFramework.scutil import generate_next_blocks, bootstrap_sidechain_nodes, start_sc_nodes
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.createPrivateKey25519 import  http_wallet_createPrivateKey25519
from httpCalls.transaction.findTransactionByID import http_transaction_findById
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.block.best import http_block_best
from SidechainTestFramework.websocket_client import WebsocketClient
import time

"""
The purpose of this test is to verify the websocket server inside the SC node

Network Configuration:
    1 MC nodes, 1 SC node and 1 websocket client

Workflow modelled in this test:
    McNode: send some money to SCNode1 (forward transfer)
    Test mempool requests:
        -Ask for mempool with no transactions inside it and verify it is empty
        -Ask for a specific mempool non existing transaction
        -Send some coins to another publicKey
        -Ask for mempool and verify the that the new transaction is returned
        -Ask for this specific transaction and some other non existing transactions and verify that
         the only existing transaction is returned
        -Ask for > max number of mempool transactions and verify the error returned
        -Mine a block
        -Ask again for mempool and verify that it's empty
    Test get single block request:
        -Ask for last block using its hash
        -Ask for last block using height
        -Verify the correctness of the responses using the /findBlockByID SC HTTP endpoint
        -Ask for a non existing block hash and verify the error returned
        -Ask for a non existing block height and verify the error returned
    Test get new block hashes request:
        -Ask with no blocks in common and verify it start answer with the first block
        -Ask with some blocks in common and some not in common and verify it start with the more recent block in common
        -Ask with the last block and verify it answer with the same block
    Test updateTip and mempool changed events:
        -Generate 1 block
        -Verify that both event are triggered
        -Send some coin to previous public key
        -Verify that mempool changed event is triggered
    Test that we don't return FeePaymentTransactions in case of no transactions in the epoch
        -Generate block in order to complete the epoch
        -Advance of another epoch without sending any transaction
        -Verify that updateTip event and get single block request of the last epoch block don't return FeePaymentTransaction
"""

class SCWsServer(SidechainTestFramework):
    number_of_sidechain_nodes = 1
    blocks = []
    sc_withdrawal_epoch_length = 10
    websocket_server_port = 8024

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node connection to MC node
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            websocket_server_enabled=True, websocket_server_port=self.websocket_server_port
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 500, self.sc_withdrawal_epoch_length),
                                        sc_node_1_configuration)

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        logging.info("SC ws server requests test is starting...")

        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        publicKey1 = self.sc_nodes_bootstrap_info.genesis_account.publicKey
        self.sc_sync_all()

        #we need regular coins (the genesis account balance is locked into forging stake), so we perform a
        #forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      publicKey1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress())
        self.sc_sync_all()
        self.blocks.append(generate_next_blocks(sc_node1, "", 1)[0])
        self.sc_sync_all()

        #check that the wallet balance is doubled now (forging stake + the forward transfer) (we need to convert to zentoshi also)
        assert_equal(http_wallet_balance(sc_node1),  (self.sc_nodes_bootstrap_info.genesis_account_balance * 2) * 100000000)

        time.sleep(5)

        #Start websocket client
        ws = WebsocketClient()
        ws_connection = ws.create_connection(f"ws://localhost:{self.websocket_server_port}/")

        ######## Mempool requests test ########
        logging.info("######## Mempool requests test ########")

        # Test with empty mempool

        #Send raw mempool request to ws
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.SEND_RAW_MEMPOOL_REQUEST,
                                "{}"))
        #Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.SEND_RAW_MEMPOOL_REQUEST)
        #Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(int(responsePayload['size']),0)
        assert_equal(len(responsePayload['transactions']),0)

        #Send get mempool txs request to ws with non-existing txid
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_MEMPOOL_TXS_REQUEST,
                                json.dumps({"hash": ["non_existing_txid"]})))
        #Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.GET_MEMPOOL_TXS_REQUEST)
        #Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(len(responsePayload['transactions']),0)

        #Test with non-empty mempool

        #Node 1 create new publicKey
        publicKey2 = http_wallet_createPrivateKey25519(sc_node1)

        #send some coin to the user on sidechain node 2
        txid = sendCoinsToAddress(sc_node1, publicKey2, 50000000, 1000)
        self.sc_sync_all()

        #Send raw mempool request to ws
        ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.SEND_RAW_MEMPOOL_REQUEST,
                                "{}")
        response = json.loads(ws_connection.recv()) # Skip the event message of changed mempool
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.SEND_RAW_MEMPOOL_REQUEST)
        # Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(int(responsePayload['size']),1)
        assert_equal(len(responsePayload['transactions']),1)
        assert_equal(responsePayload['transactions'][0],txid)

        #Send get mempool txs request to ws with both existing and non-existing txid
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_MEMPOOL_TXS_REQUEST,
                                json.dumps({"hash": [txid,"non_existing_txid"]})))
        #Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.GET_MEMPOOL_TXS_REQUEST)
        #Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(len(responsePayload['transactions']),1)
        #The result should be the same of transactionFindByID node endpoint
        txJson = http_transaction_findById(sc_node1,txid)
        assert_equal(responsePayload['transactions'][0],txJson['transaction'])

        #Send get mempool txs request with > 10 txids, it should answer with error
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_MEMPOOL_TXS_REQUEST,
                                json.dumps({"hash": [txid,"2","3","4","5","6","7","8","9","10","11"]})))
        #Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.ERROR_MSG_TYPE,
                                0,
                                ws.GET_MEMPOOL_TXS_REQUEST)
        assert_equal(response['errorCode'], 4)
        assert_equal(response['responsePayload'],"Exceed max number of transactions (10)!")

        # Test the raw mempool request returns no transactions after mining a block

        #Node 1 generate 1 block and clear the mempool
        block = generate_next_blocks(sc_node1, "", 1)[0]
        self.blocks.append(block)
        self.sc_sync_all()
        ws_connection.recv()
        ws_connection.recv()
        #Send raw mempool request to ws
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.SEND_RAW_MEMPOOL_REQUEST,
                                "{}"))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.SEND_RAW_MEMPOOL_REQUEST)
        # Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(int(responsePayload['size']),0)
        assert_equal(len(responsePayload['transactions']),0)


        ######## Get single block request test ########
        logging.info("######## Get single block request test ########")

        # Test get single block request

        # Send get single block request with block hash
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_SINGLE_BLOCK_REQUEST,
                                json.dumps({"hash": block})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.GET_SINGLE_BLOCK_REQUEST)
        # Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(responsePayload['hash'],block)
        assert_equal(responsePayload['height'],3)

        # The block result should be the same of findBlockByID sc endpoint response
        blockJson  = http_block_findById(sc_node1,block)
        assert_equal(responsePayload['block'], blockJson['block'])
        # No fee payments expected
        assert_true('feePayments' not in responsePayload)

        # Send get single block request with block height
        response = json.loads(ws.sendMessage(ws_connection,
                               ws.REQUEST_MSG_TYPE,
                               0,
                               ws.GET_SINGLE_BLOCK_REQUEST,
                               json.dumps({"height": 3})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                               ws.RESPONSE_MSG_TYPE,
                               0,
                               ws.GET_SINGLE_BLOCK_REQUEST)
        # Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(responsePayload['hash'],block)
        assert_equal(responsePayload['height'],3)

        # The block result should be the same of findBlockByID sc endpoint response
        assert_equal(responsePayload['block'], blockJson['block'])
        # No fee payments expected
        assert_true('feePayments' not in responsePayload)

        # Test get single block request with non-existing block

        # Send get single block request with block hash
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_SINGLE_BLOCK_REQUEST,
                                json.dumps({"hash": "d1d0af586e3e01abb7c9f493cb9bbfc2ff863e87cb035325d9e22df37fb1660e"})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.ERROR_MSG_TYPE,
                                0,
                                ws.GET_SINGLE_BLOCK_REQUEST)
        assert_equal(response['errorCode'],5)
        # Verify responsePayload
        assert_equal(response['responsePayload'], "Invalid parameter")

        # Send get single block request with block height
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_SINGLE_BLOCK_REQUEST,
                                json.dumps({"height": 5})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.ERROR_MSG_TYPE,
                                0,
                                ws.GET_SINGLE_BLOCK_REQUEST)
        assert_equal(response['errorCode'],5)
        # Verify responsePayload
        assert_equal(response['responsePayload'], "Invalid parameter")

        ######## Get new block hashes request test ########
        logging.info("######## Get new block hashes request test ########")
        # Test with no hashes in common

        # Send get new block hashes request with non existing block hash
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_NEW_BLOCK_HASHES_REQUEST,
                                json.dumps({"locatorHashes": ["d1d0af586e3e01abb7c9f493cb9bbfc2ff863e87cb035325d9e22df37fb1660e"], "limit":2})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.GET_NEW_BLOCK_HASHES_REQUEST)
        # Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(responsePayload['height'],-1)
        assert_equal(len(responsePayload['hashes']),0)

        # Send get new block hashes request with block hash # 1
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_NEW_BLOCK_HASHES_REQUEST,
                                json.dumps({"locatorHashes": [self.blocks[0]], "limit":2})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.GET_NEW_BLOCK_HASHES_REQUEST)
        # Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(responsePayload['height'],3)
        assert_equal(len(responsePayload['hashes']),1)
        assert_equal(responsePayload['hashes'][0],self.blocks[1])

        # Send get new block hashes request with the last block hash
        response = json.loads(ws.sendMessage(ws_connection,
                                ws.REQUEST_MSG_TYPE,
                                0,
                                ws.GET_NEW_BLOCK_HASHES_REQUEST,
                                json.dumps({"locatorHashes": [self.blocks[0],self.blocks[1]], "limit":2})))
        # Verify static response field
        ws.checkMessageStaticFields(response,
                                ws.RESPONSE_MSG_TYPE,
                                0,
                                ws.GET_NEW_BLOCK_HASHES_REQUEST)
        # Verify responsePayload
        responsePayload = response['responsePayload']
        assert_equal(responsePayload['height'],3)
        assert_equal(len(responsePayload['hashes']),1)
        assert_equal(responsePayload['hashes'][0],self.blocks[1])

        ######## Websocket events tests ########
        logging.info("######## Websocket events test ########")

        self.blocks.append(generate_next_blocks(sc_node1, "", 1)[0])
        self.sc_sync_all()

        newTipEvent = ""
        changedMempoolEvent = ""
        for i in range (0,2):
            response = json.loads(ws_connection.recv())
            if response['msgType'] == 0 and response['answerType'] == 0: # new Tip event
                newTipEvent = response
            elif response['msgType'] == 0 and response['answerType'] == 2: # changed mempool event
                changedMempoolEvent = response
            else:
                raise Exception("Message not expected") # We shouldn't receive any other message

        # After generating a block we expect to receive new tip event
        # Verify static response field
        ws.checkMessageStaticFields(newTipEvent,
                               ws.EVENT_MSG_TYPE,
                               -1,
                               ws.UPDATE_TIP_EVENT)
        eventPayload = newTipEvent['eventPayload']
        bestBlockJson = http_block_best(sc_node1)

        assert_equal(eventPayload['hash'],bestBlockJson['id'])
        assert_equal(eventPayload['height'],4)
        assert_equal(eventPayload['block'],bestBlockJson)

        #After generating a block the mempool changes and we expect to receive changedMempool event that send the actual state of the mempool
        # Verify static response field
        ws.checkMessageStaticFields(changedMempoolEvent,
                               ws.EVENT_MSG_TYPE,
                               -1,
                               ws.MEMPOOL_CHANGED_EVENT)
        # We expect to have no txes in mempool
        eventPayload = changedMempoolEvent['eventPayload']
        assert_equal(eventPayload['size'],0)
        assert_equal(eventPayload['transactions'],[])

        #Generate 1 transaction and we expect to receive changedMempool event
        #send some coin to the user on sidechain node 2
        txid = sendCoinsToAddress(sc_node1, publicKey1, 40000000, 1000)
        self.sc_sync_all()

        changedMempoolEvent = json.loads(ws_connection.recv())
        # Verify static response field
        ws.checkMessageStaticFields(changedMempoolEvent,
                               ws.EVENT_MSG_TYPE,
                               -1,
                               ws.MEMPOOL_CHANGED_EVENT)
        # We expect to have new tx in mempool
        eventPayload = changedMempoolEvent['eventPayload']
        assert_equal(eventPayload['size'],1)
        assert_equal(eventPayload['transactions'],[txid])

        generate_next_blocks(sc_node1, "", 1)[0]
        ws_connection.recv()
        ws_connection.recv()

        mc_node.generate(8)
        generate_next_blocks(sc_node1, "", 1)[0]
        ws_connection.recv()
        ws_connection.recv()

        mc_node.generate(1)
        generate_next_blocks(sc_node1, "", 1)[0]
        ws_connection.recv()
        ws_connection.recv()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        #Next epoch
        logging.info("#Next epoch")
        assert_equal(mc_node.getscinfo(self.sc_nodes_bootstrap_info.sidechain_id)["items"][0]["state"], "ALIVE")
        assert_equal(mc_node.getscinfo(self.sc_nodes_bootstrap_info.sidechain_id)["items"][0]["epoch"], 1)

        #Test that we don't return feePayments if there aren't transactions in the epoch
        logging.info("#Test that we don't return feePayments if there aren't transactions in the epoch")
        mc_node.generate(9)
        lastBlock = generate_next_blocks(sc_node1, "", 1)[0]
        for i in range (0,2):
            response = json.loads(ws_connection.recv())
            if response['msgType'] == 0 and response['answerType'] == 0: # new Tip event
                newTipEvent = response
                eventPayload = response['eventPayload']
                assert_equal(eventPayload['block']['header']['feePaymentsHash'], '0000000000000000000000000000000000000000000000000000000000000000')
                assert_false('feePayments' in eventPayload)

        response = json.loads(ws.sendMessage(ws_connection,
                ws.REQUEST_MSG_TYPE,
                0,
                ws.GET_SINGLE_BLOCK_REQUEST,
                json.dumps({"hash": lastBlock})))
        responsePayload = response['responsePayload']
        assert_equal(responsePayload['block']['header']['feePaymentsHash'], '0000000000000000000000000000000000000000000000000000000000000000')
        assert_false('feePayments' in responsePayload)

if __name__ == "__main__":
    SCWsServer().main()
