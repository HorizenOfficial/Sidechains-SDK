import json

# execute a transaction/sendCoinsToAddress call for the account based model
def sendCoinsToAddressAccount(sidechainNode, destination_address, amount, nonce):
    j = {
        "to": destination_address,
        "value": amount,
        "nonce": nonce
    }

    request = json.dumps(j)
    response = sidechainNode.transaction_sendCoinsToAddress(request)
    if "error" in response:
        print(response)
    else:
        return response["result"]["transactionId"]
