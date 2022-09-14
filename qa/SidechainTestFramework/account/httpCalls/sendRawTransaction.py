import json


# execute a transaction/sendRawTransaction call
def sendRawTransaction(sidechainNode, *, fromAddress=None, payload):
    j = {
        "from": fromAddress,
        "payload": payload
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_sendRawTransaction(request)

    if "result" in response:
        if "transactionId" in response["result"]:
            return response["result"]["transactionId"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

