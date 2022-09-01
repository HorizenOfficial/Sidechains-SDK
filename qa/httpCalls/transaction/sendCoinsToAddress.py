import json

# execute a transaction/sendCoinsToAddress call
def sendCoinsToAddress(sidechainNode, address, amount, fee, api_key = None):
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
    if (api_key != None):
        response = sidechainNode.transaction_sendCoinsToAddress(request, api_key)
    else:
        response = sidechainNode.transaction_sendCoinsToAddress(request)
    return response["result"]["transactionId"]

def sendCointsToMultipleAddress(sidechainNode, addresses, amounts, fee):
    if (len(addresses) != len(amounts)):
        return "Addresses and amunts have differente lengths!"
    outputs = []
    for i in range(0, len(addresses)):
        outputs += [{"publicKey": addresses[i], "value": amounts[i]}]

    j = {
        "outputs": outputs,
        "fee": fee
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_sendCoinsToAddress(request)
    return response["result"]["transactionId"]

def sendCointsToMultipleAddress(sidechainNode, addresses, amounts, fee):
    if (len(addresses) != len(amounts)):
        return "Addresses and amunts have differente lengths!"
    outputs = []
    for i in range(0, len(addresses)):
        outputs += [{"publicKey": addresses[i], "value": amounts[i]}]

    j = {
        "outputs": outputs,
        "fee": fee
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_sendCoinsToAddress(request)
    return response["result"]["transactionId"]
