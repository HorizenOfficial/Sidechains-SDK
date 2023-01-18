import json


# execute a transaction/signTransaction call
def signTransaction(sidechainNode, *, fromAddress=None, payload, api_key=None):
    j = {
        "from": fromAddress,
        "transactionBytes": payload
    }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_signTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_signTransaction(request)

    if "result" in response:
        if "transactionBytes" in response["result"]:
            return response["result"]["transactionBytes"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
