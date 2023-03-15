import json
#Create a new Vrf public key
def http_wallet_createVrfSecret(sidechainNode, api_key = None):
      if (api_key != None):
            response = sidechainNode.wallet_createVrfSecret({},api_key)
      else:
            response = sidechainNode.wallet_createVrfSecret()     
      return response['result']['proposition']['publicKey']



