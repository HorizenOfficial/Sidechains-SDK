import json
#execute a transaction/createopenStakeTransaction call
def createOpenStakeTransaction(sidechainNode, boxid, address, forger_index, fee, format = False, automaticSend = False):
      j = { 
            "transactionInput": 
            { 
                "boxId": boxid 
            },
            "regularOutputProposition": address,
            "forgerListIndex": forger_index,
            "fee": fee,
            "format": format,
            "automaticSend": automaticSend
      }
      request = json.dumps(j)
      response = sidechainNode.transaction_createOpenStakeTransaction(request)
      return response["result"]

#execute a transaction/createOpenStakeTransactionSimplified call
def createOpenStakeTransactionSimplified(sidechainNode, forger_proposition, forger_index, fee, format = False, automaticSend = False):
      j = {
            "forgerProposition": forger_proposition,
            "forgerListIndex": forger_index,
            "fee": fee,
            "format": format,
            "automaticSend": automaticSend
      }
      request = json.dumps(j)
      response = sidechainNode.transaction_createOpenStakeTransactionSimplified(request)
      return response["result"]