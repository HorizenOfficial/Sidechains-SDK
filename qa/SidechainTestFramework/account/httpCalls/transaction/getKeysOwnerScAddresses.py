import json


def getKeyOwnerScAddresses(sidechainNode, *, api_key=None):

    request = json.dumps({})
    if api_key is not None:
        response = sidechainNode.transaction_getKeysOwnerScAddresses(request, api_key)
    else:
        response = sidechainNode.transaction_getKeysOwnerScAddresses(request)


    if "result" in response:
        return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

