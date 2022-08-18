import json
from typing import Tuple, Any
from eth_abi import encode_abi, decode_abi
from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_smart_contract_compile import prepare_resources, get_cwd
import os
from dataclasses import dataclass

from SidechainTestFramework.account.address_util import format_eoa, format_evm
from SidechainTestFramework.account.evm_util import CallMethod
from SidechainTestFramework.account.mk_contract_address import mk_contract_address
from enum import Enum
import SidechainTestFramework.account.ac_make_raw_tx as make_raw_tx


class EvmExecutionError(RuntimeError):
    def __init__(self, msg: str):
        super().__init__(msg)


@dataclass
class ContractFunction:
    name: str
    inputs: []
    outputs: []
    sigHash: str
    isConstructor: bool

    def encode(self, *inputs):
        if not self.isConstructor:
            encoded_string = self.sigHash + encode_abi(self.inputs, inputs).hex()
        else:
            encoded_string = encode_abi(self.inputs, inputs).hex()
        return encoded_string

    def decode(self, output):
        return decode_abi(self.outputs, output)

    def __str__(self):
        return '{} :: {}({}) -> ({})'.format(self.sigHash, self.name, ",".join(self.inputs), ",".join(self.outputs))


class SmartContract:
    """This class represents a type of smart contract. Function calls and deployments etc. are taken care of in this
    implementation, specifically the encoding and sending of transactions via http or rpc"""

    # TODO auto find nonce once implemnted if None
    # TODO auto parse output and stuff once receipts and static calls work
    def __init__(self, contract_path: str):
        """The constructor argument is a unique name of a smart contract.
        For example "ERC20" but "ERC20.sol" or "someFolder/ERC20.sol" would work too.
        The constructor makes sure the smart contracts are compiled and then looks
        for the metadata and parses it."""
        prepare_resources()
        self.__initialize_members(
            SmartContract.__load(
                SmartContract.__find_contract_data_path(
                    SmartContract.__split_path(
                        contract_path.rstrip(".sol")))))
        self.Functions = dict()
        for obj in self.Abi:
            if obj['type'] == 'function':
                input_types = []
                output_types = []
                for inp in obj['inputs']:
                    input_types.append(self.__get_input_type(inp))
                for out in obj['outputs']:
                    output_types.append(self.__get_input_type(out))
                input_tuple = '({})'.format(",".join(input_types))
                sig_hash = self.Sighashes["{}{}".format(obj['name'], input_tuple)]
                self.Functions[obj['name'] + input_tuple] = ContractFunction(obj['name'], input_types, output_types,
                                                                             sig_hash, False)
            elif obj['type'] == 'constructor':
                input_types = []
                for inp in obj['inputs']:
                    input_types.append(self.__get_input_type(inp))
                self.Functions['constructor'] = ContractFunction('constructor', input_types, [],
                                                                 '', True)

    def call_function(self, node, functionName: str, *args,
                      call_method: CallMethod = CallMethod.RPC_LEGACY,
                      fromAddress: str, toAddress: str, nonce: int = None,
                      gasLimit: int, gasPrice: int = 1, maxFeePerGas: int = 1,
                      maxPriorityFeePerGas: int = 1, value: int = 0, tag: str = 'latest'):
        """Creates an on-chain writing transaction of the function `functionName` with the encoded arguments.

                Returns:
                    tx_hash: The transaction hash of the transaction
                :param args: the arguments to call the function with, in the correct order
                :param node: The side chain node to use for rpc/http
                :param functionName: The name of the function to call. Has to include parentheses and params without spaces (e.g. `someFunction(uint256,string)`
                :param call_method: The type of transaction to create
                :param fromAddress: the sender address to use
                :param toAddress: the address of the smart contract instance to use
                :param gasLimit: The gasLimit to use
                :param gasPrice: The gasPrice to use
                :param nonce: The nonce to use. If not given, the nonce will be retrieved via rpc
                :param tag: The block tag to use when calling rpc methods
                :param value: The value to use
                :param maxFeePerGas: maxFeePerGas in EIP1559
                :param maxPriorityFeePerGas: maxPriorityFeePerGas in eip1559

        """
        data = self.raw_encode_call(functionName, *args)
        nonce = self.__ensure_nonce(node, fromAddress, nonce, tag)
        chain_id = self.__ensure_chain_id(node)
        response: Any = ''
        if call_method is CallMethod.RPC_LEGACY:
            response = node.rpc_eth_signTransaction(
                self.__make_legacy_sign_payload(from_addr=fromAddress, to=toAddress, nonce=nonce, gas_price=gasPrice,
                                                gas=gasLimit, data=data, value=value))
        elif call_method is CallMethod.RPC_EIP155:
            response = node.rpc_eth_signTransaction(
                self.__make_eip155_sign_payload(from_addr=fromAddress, to=toAddress, chain_id=chain_id, nonce=nonce,
                                                gas_price=gasPrice, gas=gasLimit, data=data, value=value))
        elif call_method is CallMethod.RPC_EIP1559:
            response = node.rpc_eth_signTransaction(
                self.__make_eip1559_sign_payload(from_addr=fromAddress, to=toAddress, chain_id=chain_id, nonce=nonce,
                                                 max_fee_per_gas=maxFeePerGas,
                                                 max_priority_fee_per_gas=maxPriorityFeePerGas, gas=gasLimit, data=data,
                                                 value=value))
        response = node.rpc_eth_sendRawTransaction(response['result'])
        return response["result"]

    def static_call(self, node, functionName: str, *args, fromAddress: str, nonce: int = None, toAddress: str,
                    gasLimit: int, gasPrice: int, value: int = 0, tag: str = 'latest'):
        """Calls a function in read-only mode and returns the data if applicable

               Parameters:
                   node:           The side chain node to use for rpc/http
                   functionName (str):   The name of the function to call. Has to include parentheses and params without spaces (e.g. `someFunction(uint256,string)`
                   *args: the arguments to call the function with, in the correct order.
                   fromAddress: the sender address to use.
                   toAddress: the address of the smart contract instance to use
                   nonce (optional): The nonce to use. If not given, the nonce will be retrieved via rpc
                   gasLimit: The gasLimit to use
                   gasPrice: The gasPrice to use
                   value: The value to use
                   tag: The block tag to use when calling rpc methods

               Returns:
                   A tuple with the included decoded data if applicable, None else
       """
        request = {
            "from": format_evm(fromAddress),
            "to": format_evm(toAddress),
            "nonce": self.__ensure_nonce(node, fromAddress, nonce, tag),
            "gasLimit": gasLimit,
            "gasPrice": gasPrice,
            "value": value,
            "data": self.raw_encode_call(functionName, *args)
        }
        response = node.rpc_eth_call(request, tag)
        if 'result' in response:
            if response['result'] is not None and len(response['result']) > 0:
                return self.raw_decode_call_result(functionName, bytes.fromhex(format_eoa(response['result'])))
            else:
                print("No return data in static_call: {}".format(str(response)))
                return None
        else:
            if response['error']['message'] == 'execution reverted' and response['error']['data'].startswith(
                    '0x08c379a0'):
                print(response['error'])
                if len(response['error']['data']) < 68:
                    raise EvmExecutionError("Execution reverted without a reason.")
                reason = decode_abi(['string'], bytes.fromhex(response['error']['data'][2:])[4:])[0]
                raise EvmExecutionError("Execution reverted. Reason: \"{}\"".format(reason))
            raise RuntimeError("Something went wrong, see {}".format(str(response)))

    def estimate_gas(self, node, functionName: str, *args, fromAddress: str, toAddress: str,
                     gasLimit: int = None, gasPrice: int = None, value: int = 0, nonce: int = None,
                     tag: str = 'latest'):
        request = {
            "from": format_evm(fromAddress),
            "to": format_evm(toAddress),
            "data": self.raw_encode_call(functionName, *args),
            "nonce": self.__ensure_nonce(node, fromAddress, nonce, tag),
            "value": value
        }
        if gasLimit is not None:
            request["gasLimit"] = gasLimit
        if gasPrice is not None:
            request["gasPrice"] = gasPrice

        response = node.rpc_eth_estimateGas(request)
        print(response)
        if 'result' in response:
            if response['result'] is not None and len(response['result']) > 0:
                return int(response['result'], 16)
            else:
                print("No return data in estimate_gas: {}".format(str(response)))
                return None
        else:
            raise EvmExecutionError("Could not estimate gas: {}".format(str(response)))

    def deploy(self, node, *args, call_method: CallMethod = CallMethod.RPC_LEGACY, fromAddress: str, nonce: int = None,
               gasLimit: int, gasPrice: int = 1, maxFeePerGas: int = 1, maxPriorityFeePerGas: int = 1,
               value: int = 0, tag: str = 'latest'):
        """Calls a function in read-only mode and returns the data if applicable
            :param node: The side chain node to use for rpc/http
            :param call_method: The tx type
            :param value:  The value to use
            :param tag: The block tag to use when calling rpc methods
            :param nonce:  The nonce to use. If not given, the nonce will be retrieved via rpc
            :param fromAddress: the sender address to use
            :param maxPriorityFeePerGas: the maxPriorityFeePerGas in eip1559
            :param gasLimit: The gasLimit to use
            :param gasPrice:  The gasPrice to use in legacy/eip155 transactions
            :param maxFeePerGas:  the maxFeePerGas in eip1559

        Returns:
            A tuple with the tx_hash and the precomputed address of the smart contract
"""
        nonce = self.__ensure_nonce(node, fromAddress, nonce, tag)
        data = self.Bytecode
        chain_id = self.__ensure_chain_id(node)

        if 'constructor' in self.Functions:
            if len(args) != len(self.Functions['constructor'].inputs):
                raise RuntimeError(
                    "Constructor missing arguments: {} were provided, but {} are necessary!".format(len(args), len(
                        self.Functions['constructor'].inputs)))
            data = data + self.Functions['constructor'].encode(*args)
        response: Any = ''

        if call_method is CallMethod.RPC_LEGACY:
            response = node.rpc_eth_signTransaction(
                self.__make_legacy_sign_payload(from_addr=fromAddress, to=None, nonce=nonce, gas_price=gasPrice,
                                                gas=gasLimit, data=data, value=value))

        elif call_method is CallMethod.RPC_EIP155:
            response = node.rpc_eth_signTransaction(
                self.__make_eip155_sign_payload(from_addr=fromAddress, to=None, chain_id=chain_id, nonce=nonce,
                                                gas_price=gasPrice, gas=gasLimit, data=data, value=value))

        elif call_method is CallMethod.RPC_EIP1559:
            response = node.rpc_eth_signTransaction(
                self.__make_eip1559_sign_payload(from_addr=fromAddress, to=None, chain_id=chain_id, nonce=nonce,
                                                 max_fee_per_gas=maxFeePerGas,
                                                 max_priority_fee_per_gas=maxPriorityFeePerGas, gas=gasLimit, data=data,
                                                 value=value))
        response = node.rpc_eth_sendRawTransaction(response['result'])
        return response["result"], to_checksum_address(mk_contract_address(fromAddress, nonce))

    def __str__(self):
        string_rep = self.Name
        if len(self.Functions) > 0:
            string_rep += ':\n\t'
            items = []
            for k in self.Functions:
                items.append(str(self.Functions[k]))
            string_rep += '\n\t'.join(items)
        return string_rep

    def raw_encode_call(self, function_name: str, *args):
        """Can be used to encode *args for function_name
            Params:
                function_name: The name of the function including parentheses and input types
                *args: the arguments to encode

            Returns:
                A bytestring of the encoded function
        """
        if function_name not in self.Functions:
            raise RuntimeError("Function {} does not exist on contract {}".format(function_name, self.Name))
        else:
            return self.Functions[function_name].encode(*args)

    def raw_decode_call_result(self, function_name: str, data):
        """Can be used to decode *args for function_name
            Params:
                function_name: The name of the function including parentheses and input types
                *args: the arguments to decode

            Returns:
                A tuple of the decoded values
        """
        if function_name not in self.Functions:
            raise RuntimeError("Function {} does not exist on contract {}".format(function_name, self.Name))
        else:
            return self.Functions[function_name].decode(data)

    def __initialize_members(self, data: Tuple[str, str, dict, str, dict]):
        name, source, abi, bytecode, sig_hashes = data
        self.Name = name
        self.Source = source
        self.Abi = abi
        self.Bytecode = bytecode
        self.Sighashes = sig_hashes

    @staticmethod
    def __make_legacy_sign_payload(*, from_addr: str, to: str = None, nonce: int, gas: int, value: int = 1,
                                   data: str = '0x', gas_price: int = 1):
        r = {
            "type": 0,
            "nonce": nonce,
            "gas": gas,
            "value": value,
            "input": data,
            "from": from_addr,
            "gasPrice": gas_price
        }
        if to is not None:
            r["to"] = to

        return r

    @staticmethod
    def __make_eip155_sign_payload(*, from_addr: str, to: str = None, nonce: int, gas: int, value: int,
                                   data: str = '0x', gas_price: int = 1, chain_id: str):
        r = {
            "type": 0,
            "nonce": nonce,
            "gas": gas,
            "value": value,
            "input": data,
            "from": from_addr,
            "gasPrice": gas_price,
            "chainId": chain_id
        }
        if to is not None:
            r["to"] = to

        return r

    @staticmethod
    def __make_eip1559_sign_payload(*, from_addr: str, to: str = None, nonce: int, gas: int, value: int,
                                    data: str = None, max_priority_fee_per_gas: int = None, max_fee_per_gas: int = None,
                                    chain_id: str):
        r = {
            "type": 2,
            "nonce": nonce,
            "gas": gas,
            "value": value,
            "input": data,
            "from": from_addr,
            "maxPriorityFeePerGas": max_priority_fee_per_gas,
            "maxFeePerGas": max_fee_per_gas,
            "chainId": chain_id
        }
        if to is not None:
            r["to"] = to

        return r

    @staticmethod
    def __load(json_file: str):
        with open(json_file) as fstream:
            metadata = json.load(fstream)
            name: str = metadata['contractName']
            source: str = metadata['sourceName']
            abi: dict = metadata['abi']
            bytecode: str = metadata['bytecode']
            sig_hashes: dict = metadata['sighashes']
        return name, source, abi, bytecode, sig_hashes

    @staticmethod
    def __split_path(contr: str):
        return contr.rstrip('.sol').split('/')[-1].split('\\')[-1]

    @staticmethod
    def __get_input_type(inp: dict):
        if inp['type'] == 'tuple':
            type = []
            for comp in inp['components']:
                type.append(SmartContract.__get_input_type(comp))

            return '(' + ','.join(type) + ')'
        return inp['type']

    @staticmethod
    def __get_input_def(inp: dict):
        if inp['type'] == 'tuple':
            type = {}
            for comp in inp['components']:
                type[comp['name']] = SmartContract.__get_input_type(comp)
            return type
        return inp['type']

    @staticmethod
    def __find_contract_data_path(contr: str):
        base = get_cwd() + "/artifacts/contracts"
        sol_files = []
        for root, __, files in os.walk(base):
            for file in files:
                if file.endswith(".json") and not file.endswith(".dbg.json") and file.rstrip(".json") == contr:
                    sol_files.append(os.path.join(root, file))
        if len(sol_files) < 1:
            raise RuntimeError(
                "Contract not found - check if a contract (not solidity file) with the name \"{}\" exists".format(
                    contr))
        if len(sol_files) > 1:
            raise RuntimeError(
                "Contract name is not unique, please change the names of the contracts so they are unique")
        return sol_files[0]

    @staticmethod
    def __ensure_nonce(node, address, nonce, tag='latest'):
        on_chain_nonce = int(node.rpc_eth_getTransactionCount(str(address), tag)['result'], 16)
        if nonce is None:
            nonce = on_chain_nonce
        return nonce

    @staticmethod
    def __ensure_chain_id(node):
        return node.rpc_eth_chainId()['result']


if __name__ == '__main__':
    # print(get_cwd())
    # try:
    #     SmartContract("path")
    # except RuntimeError as err:
    #     pass
    # try:
    #     SmartContract(
    #         "contracts/ExampleERC20Contract.sol")
    # except RuntimeError as err:
    #     pass
    # SmartContract("contracts/ExampleERC20")
    print("Loading example contract and testing encoding")
    sc = SmartContract("StorageTestContract.sol")
    print(sc)
    print(
        "Smart contract call set(string) encoding: {}".format(sc.raw_encode_call('set(string)', 'This is my message')))
