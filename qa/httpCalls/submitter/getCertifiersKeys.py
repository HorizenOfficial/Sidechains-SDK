import json

# execute a submitter/getCertifiersKeys call
def http_get_certifiers_keys(sidechainNode, withdrawal_epoch):
    j = {
        "withdrawalEpoch": withdrawal_epoch,
     }
    request = json.dumps(j)
    response = sidechainNode.submitter_getCertifiersKeys(request)
    return response["result"]