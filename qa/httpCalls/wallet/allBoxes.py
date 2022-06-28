#executes a  wallet/allBoxes call
def http_wallet_allBoxes(sidechainNode, api_key = None):
      if (api_key != None):
            response = sidechainNode.wallet_allBoxes({}, api_key)
      else:
            response = sidechainNode.wallet_allBoxes()
      return response['result']['boxes']



