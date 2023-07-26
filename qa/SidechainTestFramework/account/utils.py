import json
import logging
# 10^8
from eth_utils import add_0x_prefix
from test_framework.util import COIN

# 10 ^ 10
ZENNY_TO_WEI_MULTIPLIER = 10 ** 10


def convertZenToZennies(valueInZen):
    return int(round(valueInZen * COIN))


def convertZenniesToWei(valueInZennies):
    return int(round(ZENNY_TO_WEI_MULTIPLIER * valueInZennies))


def convertZenToWei(valueInZen):
    return convertZenniesToWei(convertZenToZennies(valueInZen))


def convertWeiToZen(valueInWei):
    valueInZen = (valueInWei / ZENNY_TO_WEI_MULTIPLIER) / COIN
    if valueInZen < 1 / COIN:
        return 0
    else:
        return valueInZen


def convertZenniesToZen(valueInZennies):
    return valueInZennies / COIN


# Account model constants: (see definition at SDK src code: WellknownAddresses.scala)
#   smart contract address for handling forger stakes
WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS = "0000000000000000000011111111111111111111"
FORGER_STAKE_SMART_CONTRACT_ADDRESS = "0000000000000000000022222222222222222222"
CERTIFICATE_KEY_ROTATION_SMART_CONTRACT_ADDRESS = "0000000000000000000044444444444444444444"
PROXY_SMART_CONTRACT_ADDRESS = "00000000000000000000AAAAAAAAAAAAAAAAAAAA"
#   address used for burning coins
NULL_ADDRESS = "0000000000000000000000000000000000000000"

# Block gas limit
BLOCK_GAS_LIMIT = 30000000


def computeForgedTxFee(sc_node, tx_hash, tracing_on=False):
    # make sure the transaction hash prefixed with 0x
    tx_hash = add_0x_prefix(tx_hash)
    # resp = sc_node.rpc_eth_getTransactionByHash(tx_hash)
    # if not 'result' in resp:
    #     raise Exception('Rpc eth_getTransactionByHash cmd failed: {}'.format(json.dumps(resp, indent=2)))
    #
    # transactionJson = resp['result']
    # if (transactionJson is None):
    #     raise Exception('Error: Transaction {} not found (not yet forged?)'.format(tx_hash))
    # if tracing_on:
    #     logging.info("tx:")
    #     logging.info(transactionJson)

    resp = sc_node.rpc_eth_getTransactionReceipt(tx_hash)
    if not 'result' in resp:
        raise Exception('Rpc eth_getTransactionReceipt cmd failed:{}'.format(json.dumps(resp, indent=2)))

    receiptJson = resp['result']
    if (receiptJson is None):
        raise Exception('Unexpected error: Receipt not found for transaction {}'.format(tx_hash))
    if tracing_on:
        logging.info("receipt:")
        logging.info(receiptJson)

    gasUsed = int(receiptJson['gasUsed'], 16)
    effectiveGasPrice = int(receiptJson['effectiveGasPrice'], 16)

    block_hash = receiptJson['blockHash']
    resp = sc_node.rpc_eth_getBlockByHash(block_hash, False)
    if not 'result' in resp:
        raise Exception('Rpc eth_getBlockByHash cmd failed:{}'.format(json.dumps(resp, indent=2)))

    blockJson = resp['result']
    if (blockJson is None):
        raise Exception('Unexpected error: block not found {}'.format(block_hash))
    if tracing_on:
        logging.info("block:")
        logging.info(blockJson)

    baseFeePerGas = int(blockJson['baseFeePerGas'], 16)

    forgerTipPerGas = effectiveGasPrice - baseFeePerGas

 #   totalTxFee = (baseFeePerGas+forgerTipPerGas)*gasUsed
    totalTxFee = effectiveGasPrice*gasUsed
    forgersPoolFee = baseFeePerGas*gasUsed
    forgerTip = forgerTipPerGas*gasUsed
    if tracing_on:
        logging.info("totalFee = {} (forgersPoolFee = {}, forgerTip = {}".format(totalTxFee, forgersPoolFee, forgerTip))

    return totalTxFee, forgersPoolFee, forgerTip
