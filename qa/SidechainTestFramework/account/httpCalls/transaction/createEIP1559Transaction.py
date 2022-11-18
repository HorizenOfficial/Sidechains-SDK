import json


# execute a transaction/createEIP1559Transaction call


def createEIP1559Transaction(sidechainNode, *, fromAddress=None, toAddress=None, nonce=None, gasLimit,
                             maxPriorityFeePerGas, maxFeePerGas, value=0, data='', signature_v=None, signature_r=None,
                             signature_s=None, api_key = None):
    j = {
        "from": fromAddress,
        "to": toAddress,
        "gasLimit": gasLimit,
        "maxPriorityFeePerGas": maxPriorityFeePerGas,
        "maxFeePerGas": maxFeePerGas,
        "value": value,
        "data": data,
        "signature_v": signature_v,
        "signature_r": signature_r,
        "signature_s": signature_s
    }
    if nonce:
        j["nonce"] = nonce
    request = json.dumps(j)
    if (api_key != None):
        response = sidechainNode.transaction_createEIP1559Transaction(request, api_key)
    else:
        response = sidechainNode.transaction_createEIP1559Transaction(request)


    if "result" in response:
        if "transactionId" in response["result"]:
            return response["result"]["transactionId"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

