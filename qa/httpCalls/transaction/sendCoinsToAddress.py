import json


# execute a transaction/sendCoinsToAddress call
def sendCoinsToAddress(sidechainNode, address, amount, fee):
    j = {
        "outputs": [
            {
                "publicKey": str(address),
                "value": amount
            }
        ],
        "fee": fee
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_sendCoinsToAddress(request)

    if "result" in response:
        if "transactionId" in response["result"]:
            return response["result"]["transactionId"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))