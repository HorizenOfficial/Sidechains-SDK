import json
#execute a transaction/makeForgerStake call
def makeForgerStake(sidechainNode, address, blockSignPublicKey, vrfPublicKey, amount, fee = 0, api_key = None):
      j = {\
            "outputs": [ \
              { \
                "publicKey": address, \
                "blockSignPublicKey": blockSignPublicKey, \
                "vrfPubKey": vrfPublicKey,     
                "value": amount \
              } \
            ], \
            "fee": fee \
      }
      request = json.dumps(j)
      if (api_key != None):
        response = sidechainNode.transaction_makeForgerStake(request, api_key)
      else:
        response = sidechainNode.transaction_makeForgerStake(request)
      return response