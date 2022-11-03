import json

# execute a node/getCertificateSigners call
def http_get_certificate_signers(sidechainNode):
    j = {}
    request = json.dumps(j)
    response = sidechainNode.transaction_getCertificateSigners(request)
    return response["result"]