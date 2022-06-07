#executes a  wallet/allPublicKeys call
def http_wallet_allPublicKeys(sidechainNode):
      response = sidechainNode.wallet_allPublicKeys()
      return response['result']['propositions']