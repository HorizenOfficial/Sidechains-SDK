import json
#execute a transaction/makeForgerStake call
def makeForgerStake(sidechainNode, address, blockSignPublicKey, vrfPublicKey, amount, fee):
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
      response = sidechainNode.transaction_makeForgerStake(request)
      return response