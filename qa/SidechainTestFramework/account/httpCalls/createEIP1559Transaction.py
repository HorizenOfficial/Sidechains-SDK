import json


# execute a transaction/createEIP1559Transaction call
def createEIP1559Transaction(sidechainNode, *, chainId, fromAddress=None, toAddress=None, nonce, gasLimit,
                             maxPriorityFeePerGas, maxFeePerGas, value=0, data='', signature_v=None, signature_r=None,
                             signature_s=None):
    j = {
        "chainId": chainId,
        "from": fromAddress,
        "to": toAddress,
        "nonce": nonce,
        "gasLimit": gasLimit,
        "maxPriorityFeePerGas": maxPriorityFeePerGas,
        "maxFeePerGas": maxFeePerGas,
        "value": value,
        "data": data,
        "signature_v": signature_v,
        "signature_r": signature_r,
        "signature_s": signature_s
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_createEIP1559Transaction(request)
    return response["result"]["transactionId"]
