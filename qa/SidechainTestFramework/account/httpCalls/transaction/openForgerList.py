import json

def open_forger_list(sc_node, forgerIndex, api_error_expected=False):
    j = {
        "forgerIndex": forgerIndex,
    }
    request = json.dumps(j)
    response = sc_node.transaction_openForgerList(request)

    if api_error_expected:
        return response
    else:
        if "result" in response:
            return response['result']
        raise RuntimeError("Something went wrong, see {}".format(str(response)))
