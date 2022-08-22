from enum import Enum

from SidechainTestFramework.account.address_util import format_eoa, format_evm
from SidechainTestFramework.account.evm_util import CallMethod


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
                               data: str = '0x', gas_price: int = 1):
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
                               data: str = '0x', gas_price: int = 1, chain_id: str):
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
                                data: str = '0x', max_priority_fee_per_gas: int = None, max_fee_per_gas: int = None,
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


def __ensure_nonce(node, address, nonce, tag='latest'):
    on_chain_nonce = int(node.rpc_eth_getTransactionCount(str(address), tag)['result'], 16)
    if nonce is None:
        nonce = on_chain_nonce
    return nonce


def __ensure_chain_id(node):
    return node.rpc_eth_chainId()['result']


def eoa_transaction(node, *,
                    call_method: CallMethod = CallMethod.RPC_LEGACY,
                    static_call: bool = False,
                    from_addr: str,
                    to_addr: str,
                    value: int,
                    gas: int = 2300,
                    gas_price: int = 1,
                    max_priority_fee_per_gas: int = None,
                    max_fee_per_gas: int = None,
                    data: str = '0x',
                    nonce: int = None,
                    tag='latest'):
    nonce = __ensure_nonce(node, address=from_addr, nonce=nonce, tag=tag)
    chain_id = __ensure_chain_id(node)
    payload = ''
    from_addr = format_evm(from_addr)
    to_addr = format_evm(to_addr)
    if static_call:
        response = node.rpc_eth_call(
            __make_static_call_payload(from_addr=from_addr, to_addr=to_addr, nonce=nonce, gas_limit=gas, value=value,
                                       data=data, gas_price=gas_price), tag)
        print(response)
        if 'result' in response:
            if response['result'] is not None and len(response['result']) > 0:
                return response['result']
            else:
                print("No return data in static_call: {}".format(str(response)))
                return None
        else:
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
    response = node.rpc_eth_sendRawTransaction(response['result'])
    return response["result"]
