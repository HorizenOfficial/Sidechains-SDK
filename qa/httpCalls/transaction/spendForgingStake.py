import json
#execute a transaction/spendForgingStake call
def spendForgingStake(sidechainNode, inputBoxId, valueRegular, valueForging, publicKey, blockSignPublicKey, vrfPubKey, api_key = None):
      j = {\
          "transactionInputs": [ \
            { \
              "boxId": inputBoxId \
            } \
          ],\
          "regularOutputs": [\
            {\
              "publicKey": publicKey,\
              "value": valueRegular\
            }\
          ],\
          "forgerOutputs": [\
            {\
              "publicKey": publicKey,\
              "blockSignPublicKey": blockSignPublicKey,\
              "vrfPubKey": vrfPubKey,\
              "value": valueForging\
            }\
          ],\
      }
      request = json.dumps(j)
      if (api_key != None):
        sidechainNode.transaction_spendForgingStake(request, api_key)
      else:
        sidechainNode.transaction_spendForgingStake(request)
