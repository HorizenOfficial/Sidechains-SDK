import json
#execute a transaction/openStake call
def createOpenStakeTransaction(sidechainNode, boxid, address, forger_index, fee, format = False):
      j = { 
            "transactionInput": 
            { 
                "boxId": boxid 
            },
            "regularOutputProposition": address,
            "forgerListIndex": forger_index,
            "fee": fee,
            "format": format 
      }
      request = json.dumps(j)
      response = sidechainNode.transaction_createOpenStakeTransaction(request)
      return response["result"]