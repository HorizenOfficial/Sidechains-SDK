#!/usr/bin/env python3
import logging
import time
from decimal import Decimal
from eth_utils import add_0x_prefix, remove_0x_prefix
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.scutil import generate_next_block
from httpCalls.transaction.allTransactions import allTransactions
from SidechainTestFramework.account.utils import convertZenToWei
from test_framework.util import assert_equal, assert_true

"""
Configuration: 
    - 2 SC nodes connected with each other
    - 1 MC node

Test:
    Generate a huge number of ft outputs in some FT for this SC in order to have a large set of MC ref data to be
    included in the next SC block. Verify that only up to 5 MB of the SC block size is used for MC ref data.
    Moreover send many Eth txes having large size and check that only up to 2 MB of the remaining block size is used
    for including tx.
     
"""


class SCEvmBlockSizeLimit(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, max_nonce_gap=100, max_account_slots=100)

    def sendEoa2EoaWithData(self, from_sc_node, to_sc_node, from_addr, to_addr, amount_in_zen, *,
                    nonce=None, data=None, gasLimit=21000):

        amount_in_wei = convertZenToWei(amount_in_zen)

        try:
            tx_hash = createLegacyTransaction(from_sc_node,
                fromAddress=from_addr,
                toAddress=to_addr,
                value=amount_in_wei,
                nonce=nonce,
                data=data,
                gasLimit=gasLimit
            )
        except RuntimeError as err:
            logging.info("Expected exception thrown: {}".format(err))
            return False, "send failed: " + str(err), None

        # get mempool contents and check contents are as expected
        response = allTransactions(from_sc_node, False)
        assert_true(tx_hash in response['transactionIds'])


    def sc_setup_chain(self):
        # increase rest api timeout otherwise we might not be able to forge a huge block
        # on dev machine it takes a little less than 10 secs
        self.options.restapitimeout = 20
        super().sc_setup_chain()

    def dump_delta_t(self, t_1, tag):
        t_2 = time.time()
        delta_t = t_2 - t_1
        logging.info("{} - delta t = {}".format(tag, delta_t))
        return time.time()

    def run_test(self):

        mc_node = self.nodes[0]
        ft_amount_in_zen = Decimal('500.0')
        ft_amount_in_zen_2 = Decimal('0.00001')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        mc_node.getbestblockhash()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        evm_address_sc1 = remove_0x_prefix(self.evm_address)
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # create huge FTs for filling up the MC ref data. This constant allows us to have tx with size ~90K, a little
        # below the 100K MC limit and moreover stay within the max FT number CommTree limit of 4095 FT per sidechain
        outputs_in_ft = 1000

        mc_return_address = mc_node.getnewaddress()

        logging.info("generating {} addresses, it may take some time...".format(outputs_in_ft))
        addresses = []
        t_0 = time.time()
        for k in range(0, outputs_in_ft):
            addresses.append(sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"])
        self.dump_delta_t(t_0, "Addresses generated")

        # we have this number of big tx in one block, that implies a block size a little less than 400K
        ft_in_block = 4

        # number of block to mine. They will be tried to be referenced by the next SC block. Each MC block built in this way
        # will generate MC ref data of more than 500K, therefore only 9 of them should be included in the SC block
        # 9 blocks * 4 batch * 1000 ft = 36000 ft outs
        mc_blocks = 10

        for i in range(0, mc_blocks):
            for n in range(0, ft_in_block):

                ft_args = []
                for k in range(0, outputs_in_ft):

                    ft_args.append({
                        "toaddress": str(addresses[k]),
                        "amount": ft_amount_in_zen_2,
                        "scid": str(self.sc_nodes_bootstrap_info.sidechain_id),
                        "mcReturnAddress": mc_return_address
                    })
                transaction_id = mc_node.sc_send(ft_args)
                logging.info("FT transaction id: {0}".format(transaction_id))
                time.sleep(1.1)
                tx_json = mc_node.getrawtransaction(transaction_id, 1)
                logging.info("sent tx with sz {} [{}]".format(tx_json['size'], transaction_id))

            bh = mc_node.generate(1)
            block = mc_node.getblock(bh[0], True)
            logging.info("Generated MC block with sz {} [{}]".format(block['size'], bh[0]))

            self.sync_all()


        # number of txes that fit in a block as far as gas usage is concerned (30000000 / 1331720 = ~22)
        numOfLargeTxes = 22

        amount_in_zen = Decimal('0.1')

        # the mempool limit for a tx size is 128 Kb, therefore we set data to 126 Kb
        # tx intrinsic gas should be (16+4)*63*1024 + 21000 = 1311240
        big_data = '0001' * 63 * 1024

        # time snapshot
        for n in range(0, numOfLargeTxes):
            logging.info("Creating an EOA to EOA transaction...")
            self.sendEoa2EoaWithData(sc_node_1, sc_node_2, evm_address_sc1, evm_address_sc2,
                                       amount_in_zen, data=big_data, gasLimit=1331720,nonce=n)

        self.sc_sync_all(mempool_cardinality_only=True)

        response = allTransactions(sc_node_1, False)
        assert_true(len(response['transactionIds']), numOfLargeTxes)

        logging.info("generating next block...")
        t_0 = time.time()
        bl = generate_next_block(sc_node_1, "first node")
        self.dump_delta_t(t_0, "Block {} generated".format(bl))

        self.sc_sync_all(mempool_cardinality_only=True)

        sc_best_block = sc_node_1.block_best()["result"]['block']

        # verify we have all mc headers but not all mc ref data since we hit the block size limit
        lenMcRefDataList = len(sc_best_block['mainchainBlockReferencesData'])
        lenMcHeadersList = len(sc_best_block['mainchainHeaders'])

        assert_equal(lenMcHeadersList, mc_blocks)
        assert_equal(lenMcRefDataList, mc_blocks-1)

        blockSize = sc_best_block['size']
        blockTxList = sc_best_block['sidechainTransactions']
        numOfTxInBlock = len(blockTxList)
        totTxSize = 0
        for tx in blockTxList:
            totTxSize += tx['size']

        blockOverheadSize = blockSize - totTxSize
        totMcRefDataSize = 0
        for i in range(0, lenMcRefDataList):
            totMcRefDataSize += int(
                sc_best_block['mainchainBlockReferencesData'][i]['sidechainRelatedAggregatedTransaction']['size'])
            assert_equal(len(
                sc_best_block['mainchainBlockReferencesData'][i]['sidechainRelatedAggregatedTransaction']['newBoxes']),
                         outputs_in_ft * ft_in_block)  # = 1000 * 4

        print("Block size: {}".format(blockSize))
        print("Num of txes in block {}".format(numOfTxInBlock))
        print("Total tx size: {}".format(totTxSize))
        print("Block overhead size: {}".format(blockOverheadSize))
        print("Block mcRefData size: {}".format(totMcRefDataSize))

        BLOCK_OVERHEAD_MAX_SIZE = 5*1000*1000
        BLOCK_MAX_SIZE = 7*1000*1000

        assert_true(blockOverheadSize < BLOCK_OVERHEAD_MAX_SIZE)
        assert_true(blockSize < BLOCK_MAX_SIZE)

        # we could not include all transactions
        assert_true(numOfTxInBlock < numOfLargeTxes)
        response = allTransactions(sc_node_1, False)
        assert_true(len(response['transactionIds']), numOfLargeTxes - numOfTxInBlock)

        # but we are below gas limit
        assert_true(sc_best_block['header']['gasUsed'] < sc_best_block['header']['gasLimit'])

        # including one more tx would have created too big a block
        assert_true((blockSize + int(totTxSize)/numOfTxInBlock) > BLOCK_MAX_SIZE)

        logging.info("generating next block...")
        t_0 = time.time()
        bl = generate_next_block(sc_node_1, "first node")
        self.dump_delta_t(t_0, "Block {} generated".format(bl))

        self.sc_sync_all()


if __name__ == "__main__":
    SCEvmBlockSizeLimit().main()
