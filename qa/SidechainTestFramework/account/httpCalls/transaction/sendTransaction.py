import json


# execute a transaction/sendRawTransaction call
def sendTransaction(sidechainNode, *, payload, api_key=None):
    j = {
        "transactionBytes": payload
    }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_sendTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_sendTransaction(request)

    if "result" in response:
        if "transactionId" in response["result"]:
            return response["result"]["transactionId"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

