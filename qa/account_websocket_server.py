#!/usr/bin/env python3
import logging

from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import CallMethod, deploy_smart_contract, \
    contract_function_static_call, contract_function_call, ac_makeForgerStake
from SidechainTestFramework.scutil import generate_next_block, disconnect_sc_nodes_bi, connect_sc_nodes, sync_sc_blocks
from test_framework.util import assert_equal, forward_transfer_to_sidechain
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account_websocket_client import AccountWebsocketClient
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenToWei
import pprint
import json


global_call_method = CallMethod.RPC_EIP155

"""
Check account websocket:
    -subscription/unsubscription
    -newHeads method
    -newPendingTransactions method
    -logs method

Configuration: bootstrap 2 SC nodes and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 2 SC nodes
    - Extract genesis info
    - Start SC nodes with that genesis info
    - The SC node 1 starts the account websocket
    
Test:
    - Send a FT to the SC node 2
    - Create a stake on SC node 2 and advance of 2 consensus epochs
    - Subscribe to the ws method "newHeads" and verify that we receive a subscription id
    - SC node 1 forge a new block
    - Verify that we receive the new block through the ws
    - Disconnect the SC node 1 and the SC node 2
    - SC node 1 generate 1 block
    - SC node 2 generate 3 blocks
    - Reconnect the SC nodes
    - Verify that we receive the ws events for the block forged by the SC node 1 and the 3 blocks forged by the SC node 2
    - Verify that we are able to make another subscription to the "newHeads" method
    - Unsubscribe to all the "newHeads" subscriptions
    - Subscribe to the ws method "newPendingTransactions" and verify that we receive a subscription id
    - SC node 2 send some ZEN to the the SC node 1 and forge a block
    - Verify that we don't receive any ws event
    - SC node 1 send some ZEN to the SC node 2 and forge a block
    - Verify that we receive a new ws event with the last transaction hash
    - Generate a new address (A2) on SC node 1 and send some ZEN to it from the SC node 2 and forge a block
    - Verify that we don't receive any ws event
    - SC node 1 send some ZEN from A2 to the SC node 2 and forge a block
    - Verify that we receive a new ws event with the last transaction hash
    - Disconnect the SC node 1 and the SC node 2
    - SC node 1 send some ZEN to the SC node 2 and forge a block
    - Verify that we receive a new ws event with the last transaction hash
    - SC node 2 generates 3 blocks
    - Reconnect the SC nodes
    - Verify that we receive again a ws event for the last transaction hash since it was sent back to the mempool
    - Unsubscribe to the "newPendingTransactions" method    
    - Deploy the ERC20 smart contract
    - SC node 1 subscribe to "logs" method using the new SM address as filter
    - Transfer some tokens from one address to another (emit Transfer event) and mine a new block
    - Verify that we receive a new ws event containing the receipt of the last transaction
    - Unsubscribe to the "logs" method
    - SC node 1 subscribe to "logs" method using the new SM address as filter plus an additional address not inlcuded
    - Transfer some tokens from one address to another (emit Transfer event) and mine a new block
    - Verify that we receive a new ws event containing the receipt of the last transaction
    - Unsubscribe to the "logs" method
    - SC node 1 subscribe to logs method using the new SM address and some topics as filter (not all)
    - Transfer some tokens from one address to another (emit Transfer event) and mine a new block
    - Verify that we received the ws event containing the transaction logs with all the topics inside the transaction log
    - Unsubscribe to the "logs" method
    - SC node 1 subscribe to logs method using the new SM address and some topics as filter with one topic not used
    - Transfer some tokens from one address to another (emit Transfer event) and mine a new block
    - Verify that we received the ws event containing the transaction logs without the not used topic
    - Unsubscribe to the "logs" method
    - SC node 1 subscribe to logs method using the new SM address and a topic which is not included in the transaction log
    - Transfer some tokens from one address to another (emit Transfer event) and mine a new block
    - Verify that we don't receive any ws event
    - Unsubscribe to the "logs" method
    - SC node 1 subscribe to "logs" method using the new SM address as filter
    - Disconnect the SC node 1 and the SC node 2
    - Transfer some tokens from one address to another (emit Transfer event) and mine a new block
    - Verify that we received the ws event containing the transaction logs
    - SC node 2 generates 3 blocks
    - Reconnect the SC nodes
    - Verify that we receive the ws event containing the previous transaction with the property removed = True
    - SC node 1 generate 1 block
    - Verify that we received againt the ws event containing the previous transaction with the property removed = False
"""


class SCWsAccountServerTest(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=20, number_of_sidechain_nodes=2)
    
    def checkWsResponseStaticField(self, response, method, subscription):
        assert_equal(response["jsonrpc"], "2.0")
        assert_equal(response["method"], method)
        assert_equal(response["params"]["subscription"], subscription)


    def checkWsBlockResponse(self, wsBlock, rpcBlock):
        assert_equal(wsBlock["difficulty"], "0x0")
        assert_equal(wsBlock["extraData"], rpcBlock["extraData"])
        assert_equal(wsBlock["gasLimit"], rpcBlock["gasLimit"])
        assert_equal(wsBlock["gasUsed"], rpcBlock["gasUsed"])
        assert_equal(wsBlock["logsBloom"], rpcBlock["logsBloom"])
        assert_equal(wsBlock["miner"], rpcBlock["miner"])
        assert_equal(wsBlock["nonce"], rpcBlock["nonce"])
        assert_equal(wsBlock["number"], rpcBlock["number"])
        assert_equal(wsBlock["parentHash"], rpcBlock["parentHash"])
        assert_equal(wsBlock["receiptRoot"], rpcBlock["receiptsRoot"])
        assert_equal(wsBlock["sha3Uncles"], rpcBlock["sha3Uncles"])
        assert_equal(wsBlock["stateRoot"], rpcBlock["stateRoot"])
        assert_equal(wsBlock["timestamp"], rpcBlock["timestamp"])
        assert_equal(wsBlock["transactionsRoot"], rpcBlock["transactionsRoot"])

    def checkWsLogResponse(self, wsLog, rpcLog, address):
        assert_equal(wsLog["address"], address)
        assert_equal(wsLog["blockHash"], rpcLog["blockHash"])
        assert_equal(wsLog["blockNumber"], rpcLog["blockNumber"])
        assert_equal(wsLog["data"], rpcLog["logs"][0]["data"])
        assert_equal(wsLog["logIndex"], rpcLog["logs"][0]["logIndex"])
        assert_equal(wsLog["transactionHash"], rpcLog["logs"][0]["transactionHash"])
        assert_equal(wsLog["transactionIndex"], rpcLog["logs"][0]["transactionIndex"])
        assert_equal(wsLog["topics"], rpcLog["logs"][0]["topics"])


    def run_test(self):
        SC_NODE1_ID = 1

        # Start websocket client
        logging.info("Start websocket client")

        ws = AccountWebsocketClient()
        ws_connection = ws.create_connection("ws://localhost:8025/")

        sc_node = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        self.sc_ac_setup()

        # Send a FT to the SC node 2
        logging.info("Send a FT to the SC node 2")

        sc_node1_address = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        sc_node2_address = sc_node2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                        self.nodes[0],
                                        sc_node2_address,
                                        50,
                                        self.mc_return_address)

        self.block_id = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_node2_vrfPubKey = sc_node2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        sc_node2_blockSignPubKey = sc_node2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]

        result = ac_makeForgerStake(sc_node2, sc_node2_address, sc_node2_blockSignPubKey, sc_node2_vrfPubKey,
                                    convertZenToZennies(33))
        self.sc_sync_all()
            
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # Checking the receipt
        tx_id = result['result']['transactionId']
        receipt = sc_node2.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_id))
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Make forger stake with native smart contract as owner should create a failed tx")

        self.block_id = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        self.block_id = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        ############## newHeads ########################
        logging.info("############## newHeads ########################")

        # SC node 1 subscribe to newHeads method
        logging.info("SC node 1 subscribe to newHeads method")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, [ws.NEW_HEADS_SUBSCRIPTION]))
        new_heads_subscription = response["result"]
        
        # Generate 1 block and verify we received it
        logging.info("Generate 1 block and verify we received it")

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        response = json.loads(ws_connection.recv())
        rpc_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, new_heads_subscription)
        self.checkWsBlockResponse(response["params"]["result"], rpc_best_block["result"])

        
        # Disconnect SC node 1 and SC node 2
        logging.info("Disconnect SC node 1 and SC node 2")

        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)

        # SC node 2 generate 3 blocks
        logging.info("SC node 2 generate 3 blocks")

        generate_next_block(sc_node2, "second node")
        generate_next_block(sc_node2, "second node")
        generate_next_block(sc_node2, "second node")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal("0xb", node2_best_block["result"]["number"]) #height = 11

        # SC node 1 generate 1 block
        logging.info("SC node 1 generate 1 block")

        generate_next_block(sc_node, "first node")
        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal("0x9", node1_best_block["result"]["number"]) #height = 9

        # Reconnect the nodes and verify that we receive the 3 blocks from the SC node 2
        logging.info("Reconnect the nodes and verify that we receive the 3 blocks from the SC node 2")

        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)

        # Reverted block genrated from SC node 1
        response = json.loads(ws_connection.recv())
        # 1st block generated by the SC node 2
        response = json.loads(ws_connection.recv())
        # 2nd block generated by the SC node 2
        response = json.loads(ws_connection.recv())
        # 3rd block generated by the SC node 2
        response = json.loads(ws_connection.recv())

        # Verify that we are able to create another newHeads subscription
        logging.info("Verify that we are able to create another newHeads subscription")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, [ws.NEW_HEADS_SUBSCRIPTION]))
        new_heads_subscription2 = response["result"]

        # SC node 1 Unsubscribe to newHeads subscription
        logging.info("SC node 1 Unsubscribe to newHeads subscription")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.UNSUBSCRIBE_REQUEST, [new_heads_subscription, new_heads_subscription2]))

        # Verify that we don't receive any new block message
        logging.info("Verify that we don't receive any new block message")

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        ############## newPendingTransactions ########################
        logging.info("############## newPendingTransactions ########################")
      
        # SC node 1 subscribe to newPendingTransactions method
        logging.info("SC node 1 subscribe to newPendingTransactions method")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, [ws.NEW_PENDING_TRANSACTIONS_SUBSCRIPTION]))
        # Implicitly here we are verifyng that we didn't receive the event for the last block, meaning that we unsubscribed successfully from the newHeads
        new_pending_transactions_subscription = response["result"]

        # SC node 2 send some zen to SC node 1
        logging.info("SC node 2 send some zen to SC node 1")

        createLegacyTransaction(sc_node2,
                                    toAddress=sc_node1_address,
                                    value=convertZenToWei(5),
                                    )
        self.sc_sync_all()
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # SC node 1 send some zen to SC node 2
        logging.info("SC node 1 send some zen to SC node 2")

        tx_hash_2 = createLegacyTransaction(sc_node,
                                    fromAddress=sc_node1_address,
                                    toAddress=sc_node2_address,
                                    value=convertZenToWei(3),
                                    )

        self.sc_sync_all()
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # Verify that we receive the ws event only for the tx 2 because the first one was not sent by the SC node 1
        logging.info("Verify that we receive the ws event only for the tx 2 because the first one was not sent by the SC node 1")

        response = json.loads(ws_connection.recv())
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, new_pending_transactions_subscription)
        assert_equal(response["params"]["result"], add_0x_prefix(tx_hash_2))

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # Generate one new address on SC Node 1
        logging.info("Generate one new address on SC Node 1")

        sc_node1_address2 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # Send some zen to it
        logging.info("Send some zen to it")

        createLegacyTransaction(sc_node2,
                                    toAddress=sc_node1_address2,
                                    value=convertZenToWei(5),
                                    )
        self.sc_sync_all()
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # Send some zen from it and verify that we receive this new tx from the ws
        logging.info("Send some zen from it and verify that we receive this new tx from the ws")

        tx_hash_2 = createLegacyTransaction(sc_node,
                            fromAddress=sc_node1_address2,
                            toAddress=sc_node2_address,
                            value=convertZenToWei(3),
                            )
        self.sc_sync_all()

        response = json.loads(ws_connection.recv())
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, new_pending_transactions_subscription)
        assert_equal(response["params"]["result"], add_0x_prefix(tx_hash_2))

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # Disconnect SC node 1 and SC node 2
        logging.info("Disconnect SC node 1 and SC node 2")

        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)

        # Send some zen from it and verify that we receive this new tx from the ws
        logging.info("Send some zen from it and verify that we receive this new tx from the ws")

        tx_hash_3 = createLegacyTransaction(sc_node,
                            fromAddress=sc_node1_address2,
                            toAddress=sc_node2_address,
                            value=convertZenToWei(1),
                            )
        
        response = json.loads(ws_connection.recv())
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, new_pending_transactions_subscription)
        assert_equal(response["params"]["result"], add_0x_prefix(tx_hash_3))

        # SC node 1 generate 1 block
        logging.info("SC node 1 generate 1 block")

        generate_next_block(sc_node, "first node")

        # SC node 2 generate 3 blocks
        logging.info("SC node 2 generate 3 blocks")

        generate_next_block(sc_node2, "second node")
        generate_next_block(sc_node2, "second node")
        generate_next_block(sc_node2, "second node")

        # Reconnect the nodes and verify that we resend the tx
        logging.info("Reconnect the nodes and verify that we resend the tx")

        connect_sc_nodes(self.sc_nodes[0], 1)
        sync_sc_blocks(self.sc_nodes, wait_for=60)

        response = json.loads(ws_connection.recv())
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, new_pending_transactions_subscription)
        assert_equal(response["params"]["result"], add_0x_prefix(tx_hash_3))

        # SC node 1 Unsubscribe to newPendingTransactions subscription
        logging.info("SC node 1 Unsubscribe to newPendingTransactions subscription")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.UNSUBSCRIBE_REQUEST, [new_pending_transactions_subscription]))

        ############## logs ########################
        logging.info("############## logs ########################")

        smart_contract_type = 'TestERC20'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        erc20_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        erc20_address = deploy_smart_contract(sc_node, erc20_contract, self.evm_address).lower()

        method = 'totalSupply()'
        res = contract_function_static_call(sc_node, erc20_contract, erc20_address, self.evm_address, method)
        assert_equal(res[0], initial_balance)

        method = 'balanceOf(address)'
        res = contract_function_static_call(sc_node, erc20_contract, erc20_address, self.evm_address, method,
                                            self.evm_address)
        assert_equal(res[0], initial_balance)

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = ret["result"]["proposition"]["address"]
        transfer_amount = 1
     
        # Test ws logs subscription on ERC20 transfer


        # SC node 1 subscribe to logs method using the new SM address as filter
        logging.info("SC node 1 subscribe to logs method using the new SM address as filter")

        logs_subscription = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, ["logs", {"address":erc20_address}]))["result"]

        # ERC20 transfer transaction
        logging.info("ERC20 transfer transaction")

        method = 'transfer(address,uint256)'
        res = contract_function_call(sc_node, erc20_contract, erc20_address, self.evm_address, method, other_address,
                               transfer_amount)

        generate_next_block(sc_node, "first node")

        # Verify that we received the transaction logs
        logging.info("Verify that we received the transaction logs")
        response = json.loads(ws_connection.recv())

        rpc_tx_receipt = sc_node.rpc_eth_getTransactionReceipt(res)
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, logs_subscription)
        self.checkWsLogResponse(response["params"]["result"], rpc_tx_receipt["result"], erc20_address)

        # SC node 1 unsubscribe to the log filter
        logging.info("SC node 1 unsubscribe to the log filter")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.UNSUBSCRIBE_REQUEST, [logs_subscription]))

        # SC node 1 subscribe to logs method using the new SM address as filter plus an additional address not inlcuded
        logging.info("SC node 1 subscribe to logs method using the new SM address as filter plus an additional address not inlcuded")

        unused_sc_node2_address = sc_node2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        logs_subscription = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, ["logs", {"address":[erc20_address, unused_sc_node2_address]}]))["result"]

        # ERC20 transfer transaction
        logging.info("ERC20 transfer transaction")

        method = 'transfer(address,uint256)'
        res = contract_function_call(sc_node, erc20_contract, erc20_address, self.evm_address, method, other_address,
                               transfer_amount)

        generate_next_block(sc_node, "first node")

        # Verify that we received the transaction logs
        logging.info("Verify that we received the transaction logs")
        response = json.loads(ws_connection.recv())

        rpc_tx_receipt = sc_node.rpc_eth_getTransactionReceipt(res)
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, logs_subscription)
        self.checkWsLogResponse(response["params"]["result"], rpc_tx_receipt["result"], erc20_address)  

        # SC node 1 unsubscribe to the log filter
        logging.info("SC node 1 unsubscribe to the log filter")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.UNSUBSCRIBE_REQUEST, [logs_subscription]))

        # Take some topics from the previous transactions
        topics_filter = rpc_tx_receipt["result"]["logs"][0]["topics"]

        # SC node 1 subscribe to logs method using the new SM address and some topics as filter (not all)
        logging.info("SC node 1 subscribe to logs method using the new SM address and some topics as filter (not all)")
        logs_subscription = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, ["logs", {"address":[erc20_address], "topics":topics_filter[:-1]}]))["result"]
   
        # ERC20 transfer transaction
        logging.info("ERC20 transfer transaction")

        method = 'transfer(address,uint256)'
        res = contract_function_call(sc_node, erc20_contract, erc20_address, self.evm_address, method, other_address,
                               transfer_amount)

        generate_next_block(sc_node, "first node")

        # Verify that we received the transaction logs with all the topics inside the transaction log
        logging.info("Verify that we received the transaction logs with all the topics inside the transaction log")
        response = json.loads(ws_connection.recv())

        rpc_tx_receipt = sc_node.rpc_eth_getTransactionReceipt(res)
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, logs_subscription)
        self.checkWsLogResponse(response["params"]["result"], rpc_tx_receipt["result"], erc20_address)  

        # SC node 1 unsubscribe to the log filter
        logging.info("SC node 1 unsubscribe to the log filter")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.UNSUBSCRIBE_REQUEST, [logs_subscription]))

        # SC node 1 subscribe to logs method using the new SM address and some topics as filter with one topic not used
        logging.info("SC node 1 subscribe to logs method using the new SM address and some topics as filter with one topic not included")
        logs_subscription = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, ["logs", {"address":[erc20_address], "topics":topics_filter+["0xedf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"]}]))["result"]
   
        # ERC20 transfer transaction
        logging.info("ERC20 transfer transaction")

        method = 'transfer(address,uint256)'
        res = contract_function_call(sc_node, erc20_contract, erc20_address, self.evm_address, method, other_address,
                               transfer_amount)

        generate_next_block(sc_node, "first node")

        # Verify that we received the transaction logs without the not used topic
        logging.info("Verify that we received the transaction logs")
        response = json.loads(ws_connection.recv())

        rpc_tx_receipt = sc_node.rpc_eth_getTransactionReceipt(res)
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, logs_subscription)
        self.checkWsLogResponse(response["params"]["result"], rpc_tx_receipt["result"], erc20_address)  

        # SC node 1 unsubscribe to the log filter
        logging.info("SC node 1 unsubscribe to the log filter")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.UNSUBSCRIBE_REQUEST, [logs_subscription]))


        # SC node 1 subscribe to logs method using the new SM address and a topic which is not included in the transaction log
        logging.info("SC node 1 subscribe to logs method using the new SM address and a topic which is not included in the transaction log")
        logs_subscription = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, ["logs", {"address":[erc20_address], "topics":["0xedf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"]}]))["result"]
   
        # ERC20 transfer transaction
        logging.info("ERC20 transfer transaction")

        method = 'transfer(address,uint256)'
        res = contract_function_call(sc_node, erc20_contract, erc20_address, self.evm_address, method, other_address,
                               transfer_amount)

        generate_next_block(sc_node, "first node")

        # SC node 1 unsubscribe to the log filter
        logging.info("SC node 1 unsubscribe to the log filter")

        # By checking the result field we verify that we didn't receive any websocket event for the previously transaction but only the result from the unsubscription
        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.UNSUBSCRIBE_REQUEST, [logs_subscription]))
        assert_equal(response["result"], True)

        # SC node 1 subscribe to logs method using the new SM address
        logging.info("SC node 1 subscribe to logs method using the new SM address and a topic which is not included in the transaction log")
        logs_subscription = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, ["logs", {"address":[erc20_address]}]))["result"]
   
        # Disconnect SC node 1 and SC node 2
        logging.info("Disconnect SC node 1 and SC node 2")

        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)

        # ERC20 transfer transaction
        logging.info("ERC20 transfer transaction")

        method = 'transfer(address,uint256)'
        res = contract_function_call(sc_node, erc20_contract, erc20_address, self.evm_address, method, other_address,
                               transfer_amount)  

        # SC node 1 generate 1 block
        logging.info("SC node 1 generate 1 block")

        generate_next_block(sc_node, "first node")

        response = json.loads(ws_connection.recv())
        rpc_tx_receipt = sc_node.rpc_eth_getTransactionReceipt(res)
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, logs_subscription)
        self.checkWsLogResponse(response["params"]["result"], rpc_tx_receipt["result"], erc20_address)  

        # SC node 2 generate 3 blocks
        logging.info("SC node 2 generate 3 blocks")

        generate_next_block(sc_node2, "second node")
        generate_next_block(sc_node2, "second node")
        generate_next_block(sc_node2, "second node")

        # Reconnect the nodes and verify that we resend the tx with the property removed = True
        logging.info("Reconnect the nodes and verify that we resend the tx with the property removed = True")

        connect_sc_nodes(self.sc_nodes[0], 1)
        sync_sc_blocks(self.sc_nodes, wait_for=60)

        response = json.loads(ws_connection.recv())
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, logs_subscription)
        self.checkWsLogResponse(response["params"]["result"], rpc_tx_receipt["result"], erc20_address)  
        assert_equal(response["params"]["removed"], True)

        logging.info("SC node 1 generate 1 block")
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        response = json.loads(ws_connection.recv())
        rpc_tx_receipt = sc_node.rpc_eth_getTransactionReceipt(res)
        self.checkWsResponseStaticField(response, ws.SUBSCRIBE_RESPONSE, logs_subscription)
        self.checkWsLogResponse(response["params"]["result"], rpc_tx_receipt["result"], erc20_address)  
        assert_equal(response["params"]["removed"], False)
        
        ws_connection.close()


if __name__ == "__main__":
    SCWsAccountServerTest().main()
