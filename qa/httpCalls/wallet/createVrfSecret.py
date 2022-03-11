import json
#Create a new Vrf public key
def http_wallet_createVrfSecret(sidechainNode):
      response = sidechainNode.wallet_createVrfSecret()
      return response['result']['proposition']['publicKey']



