#!/usr/bin/env python3
import logging
import pprint
from binascii import a2b_hex, b2a_hex
from decimal import Decimal

import rlp
from eth_utils import add_0x_prefix, remove_0x_prefix
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createRawEIP1559Transaction import createRawEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.createRawLegacyEIP155Transaction import \
    createRawLegacyEIP155Transaction
from SidechainTestFramework.account.httpCalls.transaction.createRawLegacyTransaction import createRawLegacyTransaction
from SidechainTestFramework.account.httpCalls.transaction.decodeTransaction import decodeTransaction
from SidechainTestFramework.account.httpCalls.transaction.sendTransaction import sendTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from SidechainTestFramework.scutil import generate_next_block
from httpCalls.block.best import http_block_best
from httpCalls.transaction.allTransactions import allTransactions
from SidechainTestFramework.account.utils import convertZenToWei
from test_framework.util import (assert_equal, assert_true, fail)

"""
Configuration: 
    - 2 SC nodes NOT connected with each other
    - 1 MC node

Test:
    Test ethereum transactions of type legacy/eip155/eip1559 using these steps:
    - create unsigned raw tx
    - decode raw tx
    - sign raw tx
    - send raw tx to the network
    
    Negative tests
    - Try decode, sign or send tx with invalid payload
    - Try send an tx with invalid signature to mempool
    - Try send an unsigned tx to mempool
    - Try send a tx with bad chainid to mempool
    - Try to forge a block forcing an unsigned tx (see api)
    - Try the same with a tx with bad chainid
    - Try sending a raw tx with bad payload via eth rpc
     
"""


def b2x(b):
    return b2a_hex(b).decode('ascii')


class SCEvmRawTxHttpApi(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def do_test_raw_tx(self, *, raw_tx, sc_node, evm_signer_address):
        self.sc_sync_all()

        # get mempool contents and check it is empty
        response = allTransactions(sc_node, False)
        assert_equal(0, len(response['transactionIds']))

        signed_raw_tx = signTransaction(sc_node, fromAddress=evm_signer_address, payload=raw_tx)

        tx_json = decodeTransaction(sc_node, payload=signed_raw_tx)
        assert_equal(tx_json['signed'], True)

        tx_hash = sendTransaction(sc_node, payload=signed_raw_tx)
        self.sc_sync_all()

        # get mempool contents and check tx is there
        response = allTransactions(sc_node, False)
        assert_true(tx_hash in response['transactionIds'])

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))

        status = int(receipt['result']['status'], 16)
        assert_equal(status, 1)



    def run_test(self):
        ft_amount_in_zen = Decimal('500.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = remove_0x_prefix(self.evm_address)
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        transferred_amount_in_zen = Decimal('1.2')

        raw_tx = createRawLegacyTransaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))
        tx_json = decodeTransaction(sc_node_2, payload=raw_tx)
        assert_equal(tx_json['legacy'], True)
        assert_equal(tx_json['eip155'], False)
        assert_equal(tx_json['eip1559'], False)
        assert_equal(tx_json['signed'], False)


        self.do_test_raw_tx(
            raw_tx=raw_tx, sc_node=sc_node_1, evm_signer_address=evm_address_sc1)



        raw_tx = createRawLegacyEIP155Transaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))
        tx_json = decodeTransaction(sc_node_2, payload=raw_tx)
        assert_equal(tx_json['legacy'], True)
        assert_equal(tx_json['eip155'], True)
        assert_equal(tx_json['eip1559'], False)
        assert_equal(tx_json['signed'], False)

        self.do_test_raw_tx(
            raw_tx=raw_tx, sc_node=sc_node_1, evm_signer_address=evm_address_sc1)




        raw_tx = createRawEIP1559Transaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))
        tx_json = decodeTransaction(sc_node_2, payload=raw_tx)
        assert_equal(tx_json['legacy'], False)
        assert_equal(tx_json['eip155'], False)
        assert_equal(tx_json['eip1559'], True)
        assert_equal(tx_json['signed'], False)

        self.do_test_raw_tx(
            raw_tx=raw_tx, sc_node=sc_node_1, evm_signer_address=evm_address_sc1)


        # Negative tests
        # 0.1) create a raw tx, add 1 byte at the end and verify it can not be decoded via api
        raw_tx = createRawLegacyTransaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))

        raw_tx_with_extra_byte = raw_tx + "ab"
        try:
            decodeTransaction(sc_node_2, payload=raw_tx_with_extra_byte)
            fail("Trailing bytes in raw object should not work")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            # error is raised from API since the address has no balance
            assert_true("Spurious bytes" in str(err) )

        # 0.2) verify the same with signing by api
        try:
            signTransaction(sc_node_1, fromAddress=evm_address_sc1, payload=raw_tx_with_extra_byte)
            fail("Trailing bytes in raw object should not work")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            # error is raised from API since the address has no balance
            assert_true("Spurious bytes"  in str(err) )

        # 0.3) signe the raw tx, add 1 byte at the end and verify it can not be sent by api
        signed_raw_tx = signTransaction(sc_node_1, fromAddress=evm_address_sc1, payload=raw_tx)
        raw_tx_with_extra_byte = signed_raw_tx + "cd"
        try:
            sendTransaction(sc_node_1, payload=raw_tx_with_extra_byte)
            fail("Trailing bytes in raw object should not work")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            # error is raised from API since the address has no balance
            assert_true("Spurious bytes" in str(err))

        # 1) use a wrong address for signature
        wrong_signer_address = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        raw_tx = createRawLegacyTransaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))
        try:
            signTransaction(sc_node_1, fromAddress=wrong_signer_address, payload=raw_tx)
            fail("Wrong from address for signing should not work")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            # error is raised from API since the address has no balance
            assert_true("ErrorInsufficientBalance"  in str(err) )

        self.sc_sync_all()

        # 2) use a valid but wrong signature in the raw tx
        sig_v = "1b"
        sig_r = "20d7f34682e1c2834fcb0838e08be184ea6eba5189eda34c9a7561a209f7ed04"
        sig_s = "7c63c158f32d26630a9732d7553cfc5b16cff01f0a72c41842da693821ccdfcb"

        raw_tx = createRawLegacyTransaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(Decimal('0.1')),
                                            signature_v=sig_v, signature_r=sig_r, signature_s=sig_s)
        sig_json = decodeTransaction(sc_node_2, payload=raw_tx)['signature']
        assert_equal(sig_json['v'], sig_v)
        assert_equal(sig_json['r'], sig_r)
        assert_equal(sig_json['s'], sig_s)

        try:
            sendTransaction(sc_node_1, payload=raw_tx)
            fail("Valid but wrong signature should not work")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            # error is raised from state txModify since the address resulting from the valid but wrong signature has no balance
            assert_true("Insufficient funds" in str(err) )

        self.sc_sync_all()


        # 3.1) try sending an unsigned tx
        unsigned_raw_tx = createRawLegacyEIP155Transaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(Decimal('0.1')))
        tx_json = decodeTransaction(sc_node_2, payload=unsigned_raw_tx)
        assert_equal(tx_json['signed'], False)

        try:
            sendTransaction(sc_node_1, payload=unsigned_raw_tx)
            fail("Valid but wrong signature should not work")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("is not signed" in str(err) )


        self.sc_sync_all()


        # 3.2) try forging a block with the same unsigned tx.
        #      The block is forged but the tx is not included in the block because is not signed
        bhash = generate_next_block(sc_node_1, "first node", forced_tx=[unsigned_raw_tx])
        self.sc_sync_all()
        block_json = http_block_best(sc_node_1)
        assert_equal(block_json["header"]["id"], bhash)
        assert_true(len(block_json["sidechainTransactions"]) == 0)


        self.sc_sync_all()

        # 4.1) try sending a tx with wrong chainId
        raw_tx = createRawLegacyEIP155Transaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(Decimal('0.1')))
        chainId_ok = decodeTransaction(sc_node_2, payload=raw_tx)['chainId']

        # get the last byte of chain id value in the hex representation and decrement it (bytes [43; 46] in this tx)
        #  - 1000000001 = [3B9ACA01]  --> 1000000000 = [3B9ACA00]
        tx_hex_array = list(bytearray(a2b_hex(raw_tx)))
        tx_hex_array[46] -= 1
        new_eip155_raw_tx = b2x(bytearray(tx_hex_array))

        # decode the modified unsigned tx via http api
        chainId_bad = decodeTransaction(sc_node_2, payload=new_eip155_raw_tx)['chainId']
        # check values are consistent
        assert_equal(chainId_bad, chainId_ok - 1)

        # RLP decode the modified unsigned tx. Strip off the leading byte which is part of the companion obj used by SDK
        decodedRlpList = rlp.decode(bytearray(tx_hex_array[1:]))
        # get the chain id in the 7th rlp list member
        decodedRlpChainId = int('0x' + b2x(decodedRlpList[6]), 16)
        # check values are consistent
        assert_equal(chainId_bad, decodedRlpChainId)

        bad_chainid_raw_tx = signTransaction(sc_node_1, fromAddress=evm_address_sc1, payload=new_eip155_raw_tx)

        tx_json = decodeTransaction(sc_node_1, payload=bad_chainid_raw_tx)
        assert_equal(tx_json['signed'], True)

        try:
            sendTransaction(sc_node_1, payload=bad_chainid_raw_tx)
            fail("Wrong chainId should not work")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("different from expected SC chainId" in str(err) )

        self.sc_sync_all()



        # 4.2) try forging a block with the same tx
        try:
            ret = generate_next_block(sc_node_1, "first node", forced_tx=[bad_chainid_raw_tx])
            fail("Wrong chainId should not work")
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
            assert_true("does not match network chain ID " in str(e))

        self.sc_sync_all()

        # 5.1) try sending a badly encoded RLP tx via eth rpc
        raw_tx = "96dc24d6874a9b01e4a7b7e5b74db504db3731f764293769caef100f551efadf7d378a015faca6ae62ae30a9bf5e3c6aa94f58597edc381d0ec167fa0c84635e12a2d13ab965866ebf7c7aae458afedef1c17e08eb641135f592774e18401e0104f8e7f8e0d98e3230332e3133322e39342e31333784787beded84556c094cf8528c39342e3133372e342e31333982765fb840621168019b7491921722649cd1aa9608f23f8857d782e7495fb6765b821002c4aac6ba5da28a5c91b432e5fcc078931f802ffb5a3ababa42adee7a0c927ff49ef8528c3136322e3234332e34362e39829dd4b840e437a4836b77ad9d9ffe73ee782ef2614e6d8370fcf62191a6e488276e23717147073a7ce0b444d485fff5a0c34c4577251a7a990cf80d8542e21b95aa8c5e6cdd8e3230332e3133322e39342e31333788ffffffffa5aadb3a84556c095384556c0919"
        response = sc_node_1.rpc_eth_sendRawTransaction(raw_tx)
        assert_true("error" in response)
        assert_true("Internal error" in response['error']['message'])

        # 5.2) try sending a tx via eth rpc with trailing spurious bytes
        raw_tx = "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa07c63c158f32d26630a9732d7553cfc5b16cff01f0a72c41842da693821ccdfcbef"
        response = sc_node_1.rpc_eth_sendRawTransaction(raw_tx)
        assert_true("error" in response)
        assert_true("Spurious bytes" in response['error']['data'])






if __name__ == "__main__":
    SCEvmRawTxHttpApi().main()
