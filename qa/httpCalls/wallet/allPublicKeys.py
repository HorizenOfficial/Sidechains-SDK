#executes a  wallet/allPublicKeys call
def http_wallet_allPublicKeys(sidechainNode, api_key = None):
      if (api_key != None):
            response = sidechainNode.wallet_allPublicKeys({}, api_key)
      else:
            response = sidechainNode.wallet_allPublicKeys()
      return response['result']['propositions']