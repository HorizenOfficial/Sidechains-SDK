import json


# execute a transaction/createCoreTransaction call
def http_create_core_transaction(sidechainNode, inputs, regularOutputs = [], withdrawalRequestOutputs = [], forgerOutputs = [], api_key=None):
    j = {
        "transactionInputs": inputs,
        "regularOutputs": regularOutputs,
        "withdrawalRequests": withdrawalRequestOutputs,
        "forgerOutputs": forgerOutputs,
    }
    request = json.dumps(j)
    if (api_key != None):
        response = sidechainNode.transaction_createCoreTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_createCoreTransaction(request)
    return response["result"]