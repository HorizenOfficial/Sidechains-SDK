import json

# execute a transaction/createCoreTransaction call for the account based model
def http_create_core_transaction_account(sidechainNode, address, amount, nonce, api_key=None):
    j = {
        "to": amount,
        "value": address,
        "nonce": nonce,
    }
    request = json.dumps(j)
    '''
    print('destination address ', address)
    print('amount ', amount)
    print('nonce ', nonce)
    print(request)
    '''
    if (api_key != None):
        response = sidechainNode.transaction_createCoreTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_createCoreTransaction(request)
    print(response["result"])
    return response["result"]





