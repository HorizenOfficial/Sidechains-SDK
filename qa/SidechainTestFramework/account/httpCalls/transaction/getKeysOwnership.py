import json


def getKeysOwnership(sidechainNode, *, sc_address=None, api_key=None):
    j = {
            "scAddressOpt" : sc_address
    }

    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_getKeysOwnership(request, api_key)
    else:
        response = sidechainNode.transaction_getKeysOwnership(request)


    if "result" in response:
        return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

