import json


def createRawLegacyEIP155Transaction(sidechainNode, *, fromAddress=None, toAddress=None, nonce=None, gasLimit=21000,
                            gasPrice=1000000000, value=0, data='', api_key=None):

    j = {
        "from": fromAddress,
        "to": toAddress,
        "nonce": nonce,
        "gasLimit": gasLimit,
        "gasPrice": gasPrice,
        "value": value,
        "data": data,
        "outputRawBytes": True
    }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_createLegacyEIP155Transaction(request, api_key)
    else:
        response = sidechainNode.transaction_createLegacyEIP155Transaction(request)

    if "result" in response:
        if "transactionBytes" in response["result"]:
            return response["result"]["transactionBytes"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
