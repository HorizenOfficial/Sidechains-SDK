import json


def decodeTransaction(sidechainNode, *, payload, api_key=None):
    j = {
        "transactionBytes": payload
    }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_decodeTransactionBytes(request, api_key)
    else:
        response = sidechainNode.transaction_decodeTransactionBytes(request)

    if "result" in response:
        if "transaction" in response["result"]:
            return response["result"]["transaction"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

