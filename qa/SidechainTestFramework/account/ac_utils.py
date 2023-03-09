import json
import logging
import os
import random
import subprocess
from enum import Enum

from eth_utils import to_checksum_address

from SidechainTestFramework.scutil import assert_equal, generate_next_block

cwd = None
nodeModulesInstalled = False


# helper method for EIP155 tx
def getChainIdFromSignatureV(sigV):
    return int((sigV - 35) / 2)


def get_cwd():
    global cwd
    if cwd is None:
        cwd = os.environ.get("SIDECHAIN_SDK") + "/qa/SidechainTestFramework/account/smart_contract_resources"
    return cwd


def install_npm_packages():
    global nodeModulesInstalled
    if not nodeModulesInstalled:
        logging.info("Installing node packages...")
        logging.info("The first time this runs on your machine, it can take a few minutes...")
        proc = subprocess.run(
            ["yarn", "install", "--quiet", "--non-interactive", "--frozen-lockfile"],
            cwd=get_cwd(),
            stdout=subprocess.PIPE)
        proc.check_returncode()
        logging.info("Done!")
        nodeModulesInstalled = True


def ensure_nonce(node, address, nonce, tag='latest'):
    if nonce is None:
        on_chain_nonce = int(node.rpc_eth_getTransactionCount(format_evm(address), tag)['result'], 16)
        nonce = on_chain_nonce
    return nonce


def ensure_chain_id(node):
    return node.rpc_eth_chainId()['result']


class CallMethod(Enum):
    RPC_LEGACY = 0
    RPC_EIP155 = 1
    RPC_EIP1559 = 2


def __make_static_call_payload(*, from_addr: str, to_addr: str, nonce: int, gas_limit: int, gas_price: int, value: int,
                               data: str = '0x'):
    return {
        "from": format_evm(from_addr),
        "to": format_evm(to_addr),
        "nonce": nonce,
        "gasLimit": gas_limit,
        "gasPrice": gas_price,
        "value": value,
        "data": data
    }


def __make_legacy_sign_payload(*, from_addr: str, to: str = None, nonce: int, gas: int, value: int = 1,
                               data: str = None, gas_price: int = 1):
    r = {
        "type": 0,
        "nonce": nonce,
        "gas": gas,
        "value": value,
        "from": from_addr,
        "gasPrice": gas_price
    }
    if to is not None:
        r["to"] = to
    if data is not None:
        r["input"] = data

    return r


def __make_eip155_sign_payload(*, from_addr: str, to: str = None, nonce: int, gas: int, value: int,
                               data: str = None, gas_price: int = 1, chain_id: str):
    r = {
        "type": 0,
        "nonce": nonce,
        "gas": gas,
        "value": value,
        "from": from_addr,
        "gasPrice": gas_price,
        "chainId": chain_id
    }
    if to is not None:
        r["to"] = to
    if data is not None:
        r["input"] = data

    return r


def __make_eip1559_sign_payload(*, from_addr: str, to: str = None, nonce: int, gas: int, value: int,
                                data: str = None, max_priority_fee_per_gas: int = None, max_fee_per_gas: int = None,
                                chain_id: str):
    r = {
        "type": 2,
        "nonce": nonce,
        "gas": gas,
        "value": value,
        "from": from_addr,
        "maxPriorityFeePerGas": max_priority_fee_per_gas,
        "maxFeePerGas": max_fee_per_gas,
        "chainId": chain_id
    }
    if to is not None:
        r["to"] = to
    if data is not None:
        r["input"] = data

    return r


def eoa_transaction(node, *,
                    call_method: CallMethod = CallMethod.RPC_LEGACY,
                    static_call: bool = False,
                    from_addr: str,
                    to_addr: str,
                    value: int,
                    gas: int = 21000,
                    gas_price: int = 875000000,
                    max_priority_fee_per_gas: int = 1,
                    max_fee_per_gas: int = 875000000,
                    data: str = '0x',
                    nonce: int = None,
                    tag='latest'):
    nonce = ensure_nonce(node, address=from_addr, nonce=nonce, tag=tag)
    chain_id = ensure_chain_id(node)
    payload = ''
    from_addr = format_evm(from_addr)
    to_addr = format_evm(to_addr)

    if static_call:
        response = node.rpc_eth_call(
            __make_static_call_payload(from_addr=from_addr, to_addr=to_addr, nonce=nonce, gas_limit=gas, value=value,
                                       data=data, gas_price=gas_price), tag)
        if 'result' in response:
            if response['result'] is not None and len(response['result']) > 0:
                return response['result']
            else:
                logging.info("No return data in static_call: {}".format(str(response)))
                return None
        else:
            logging.info(response)
            raise RuntimeError("No result in static call, thus error")

    if call_method == CallMethod.RPC_LEGACY:
        payload = __make_legacy_sign_payload(from_addr=from_addr, to=to_addr, nonce=nonce, gas=gas, value=value,
                                             data=data, gas_price=gas_price)
    if call_method == CallMethod.RPC_EIP155:
        payload = __make_eip155_sign_payload(from_addr=from_addr, to=to_addr, nonce=nonce, gas=gas, value=value,
                                             data=data, gas_price=gas_price, chain_id=chain_id)
    if call_method == CallMethod.RPC_EIP1559:
        payload = __make_eip1559_sign_payload(from_addr=from_addr, to=to_addr, nonce=nonce, gas=gas, value=value,
                                              data=data, max_fee_per_gas=max_fee_per_gas,
                                              max_priority_fee_per_gas=max_priority_fee_per_gas, chain_id=chain_id)

    response = node.rpc_eth_signTransaction(payload)

    if "result" in response:
        response = node.rpc_eth_sendRawTransaction(response['result'])
        if "result" in response:
            return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))


def format_evm(add: str):
    return to_checksum_address(add)


def format_eoa(add: str):
    if add.startswith('0x'):
        return add[2:].lower()
    else:
        return add.lower()


def eoa_transfer(node, sender, receiver, amount, call_method: CallMethod = CallMethod.RPC_EIP155,
                 static_call: bool = False, tag: str = 'latest'):
    if static_call:
        res = eoa_transaction(node, from_addr=sender, to_addr=receiver, value=amount, static_call=True, tag=tag)
    else:
        res = eoa_transaction(node, from_addr=sender, to_addr=receiver, call_method=call_method, value=amount)
    return res


def contract_function_static_call(node, smart_contract_type, smart_contract_address, from_address, method, *args,
                                  tag='latest'):
    logging.info("Calling {}: using static call function".format(method))
    res = smart_contract_type.static_call(node, method, *args, fromAddress=from_address,
                                          toAddress=smart_contract_address, tag=tag)
    return res


def contract_function_call(node, smart_contract_type, smart_contract_address, from_address, method, *args, value=0,
                           overrideGas=None, tag='latest'):
    logging.info("Estimating gas for contract call...")
    estimated_gas = smart_contract_type.estimate_gas(node, method, *args, value=value,
                                                     fromAddress=from_address, toAddress=smart_contract_address,
                                                     tag=tag)
    logging.info("Estimated gas is {}".format(estimated_gas))

    logging.info("Calling {}: using call function".format(method))
    res = smart_contract_type.call_function(node, method, *args, fromAddress=from_address,
                                            value=value,
                                            gasLimit=estimated_gas if overrideGas is None else overrideGas,
                                            toAddress=smart_contract_address, tag=tag)
    return res


def deploy_smart_contract(node, smart_contract, from_address, *args, call_method: CallMethod = CallMethod.RPC_LEGACY,
                          next_block=True):
    logging.info("Estimating gas for deployment...")
    estimated_gas = smart_contract.estimate_gas(node, 'constructor', *args, fromAddress=from_address)
    logging.info("Estimated gas is {}".format(estimated_gas))

    logging.info("Deploying smart contract...")
    tx_hash, address = smart_contract.deploy(node, *args, call_method=call_method,
                                             fromAddress=from_address,
                                             gasLimit=estimated_gas)

    if next_block:
        generate_next_block(node, "first node")

        tx_receipt = node.rpc_eth_getTransactionReceipt(tx_hash)
        assert_equal(tx_receipt['result']['contractAddress'], address.lower())
        logging.info("Smart contract deployed successfully to address {}".format(address))
        return address
    else:
        return address


def generate_block_and_get_tx_receipt(node, tx_hash, return_status=False):
    generate_next_block(node, "first node")
    tx_receipt = node.rpc_eth_getTransactionReceipt(tx_hash)
    if return_status:
        return int(tx_receipt['result']['status'], 0)
    return tx_receipt


def random_byte_string(*, length=20):
    return '0x' + bytes([random.randrange(0, 256) for _ in range(0, length)]).hex()


def estimate_gas(node, from_address=None, to_address=None, data='0x', value='0x0', gasPrice='0x4B9ACA00', nonce=None):
    request = {
        "from": from_address,
        "to": to_address,
        "data": data,
        "value": value,
        "gasPrice": gasPrice,
        "nonce": nonce
    }
    return node.rpc_eth_estimateGas(request)


def ac_makeForgerStake(sc_node, owner_address, blockSignPubKey, vrf_public_key, amount, nonce=None):
    forgerStakes = {"forgerStakeInfo": {
        "ownerAddress": owner_address,
        "blockSignPublicKey": blockSignPubKey,
        "vrfPubKey": vrf_public_key,
        "value": amount  # in Satoshi
    },
        "nonce": nonce
    }

    return sc_node.transaction_makeForgerStake(json.dumps(forgerStakes))
