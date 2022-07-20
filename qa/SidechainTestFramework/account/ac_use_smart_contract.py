import json
from typing import Tuple
from eth_abi import encode_abi, decode_abi
from SidechainTestFramework.account.ac_smart_contract_compile import prepare_resources, get_cwd
import os
from dataclasses import dataclass
from SidechainTestFramework.account.mk_contract_address import mk_contract_address


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
        return f'{self.sigHash} :: {self.name}({",".join(self.inputs)}) -> ({",".join(self.outputs)})'


class SmartContract:

    def __init__(self, contract_path: str):
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
                input_tuple = f'({",".join(input_types)})'
                sig_hash = self.Sighashes[f"{obj['name']}{input_tuple}"]
                self.Functions[obj['name'] + input_tuple] = ContractFunction(obj['name'], input_types, output_types,
                                                                             sig_hash, False)
            elif obj['type'] == 'constructor':
                input_types = []
                for inp in obj['inputs']:
                    input_types.append(self.__get_input_type(inp))
                self.Functions['constructor'] = ContractFunction('constructor', input_types, [],
                                                                 '', True)

    def call_function(self, node, functionName, *args, fromAddress: str, toAddress: str, nonce, gasLimit, gasPrice,
                      value=0):
        j = {
            "from": fromAddress,
            "to": toAddress,
            "nonce": nonce,
            "gasLimit": gasLimit,
            "gasPrice": gasPrice,
            "value": value,
            "data": self.raw_encode_call(functionName, *args)
        }
        request = json.dumps(j)
        response = node.transaction_createLegacyTransaction(request)
        return response["result"]["transactionId"]

    def static_call(self, node, functionName, *args, fromAddress, nonce, toAddress, gasLimit, gasPrice,
                    value=0):
        j = {
            "from": fromAddress,
            "to": toAddress,
            "nonce": nonce,
            "gasLimit": gasLimit,
            "gasPrice": gasPrice,
            "value": value,
            "data": self.raw_encode_call(functionName, *args)
        }
        request = json.dumps(j)
        response = node.rpc_eth_call(str(request), "0")
        # TODO fix when it exists
        return response

    def deploy(self, node, *args, fromAddress, nonce, gasLimit, gasPrice,
               value=0):
        j = {
            "from": fromAddress,
            "nonce": nonce,
            "gasLimit": gasLimit,
            "gasPrice": gasPrice,
            "value": value,
            "data": self.Bytecode
        }
        if 'constructor' in self.Functions:
            j['data'] = j['data'] + self.Functions['constructor'].encode(*args)

        request = json.dumps(j)
        response = node.transaction_createLegacyTransaction(request)

        # tx_hash = response["result"]["transactionId"]
        #
        # j = {
        #
        # }
        #
        # nodeView.rpc_eth_getTransactionByHash(request)
        return response["result"]["transactionId"], mk_contract_address(fromAddress, nonce)

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
        if function_name not in self.Functions:
            raise RuntimeError(f"Function {function_name} does not exist on contract {self.Name}")
        else:
            return self.Functions[function_name].encode(*args)

    def raw_decode_call_result(self, function_name: str, data):
        if function_name not in self.Functions:
            raise RuntimeError(f"Function {function_name} does not exist on contract {self.Name}")
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
                f"Contract not found - check if a contract (not solidity file) with the name \"{contr}\" exists")
        if len(sol_files) > 1:
            raise RuntimeError(
                "Contract name is not unique, please change the names of the contracts so they are unique")
        return sol_files[0]


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
    sc = SmartContract("ExampleContract.sol")
    print(sc)
    print(
        f"Smart contract call set(string) encoding: {sc.raw_encode_call('set(string)', 'This is my message')}")
