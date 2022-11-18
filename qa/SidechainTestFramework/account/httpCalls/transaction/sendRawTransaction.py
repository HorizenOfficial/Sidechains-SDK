import json


# execute a transaction/sendRawTransaction call
def sendRawTransaction(sidechainNode, *, fromAddress=None, payload, api_key=None):
    j = {
        "from": fromAddress,
        "payload": payload
    }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_sendRawTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_sendRawTransaction(request)

    if "result" in response:
        if "transactionId" in response["result"]:
            return response["result"]["transactionId"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

