import json
#execute a transaction/withdrawCoins call
def withdrawCoins(sidechainNode, mc_address, amount, fee = 0, api_key = None):
      j = {\
            "outputs": [ \
              { \
                "mainchainAddress": mc_address, \
                "value": amount \
              } \
            ], \
            "fee": fee \
      }
      request = json.dumps(j)
      if (api_key != None):
        response = sidechainNode.transaction_withdrawCoins(request, api_key)
      else:
        response = sidechainNode.transaction_withdrawCoins(request)
      return response


def withdrawMultiCoins(sidechainNode, mc_addresses, amounts, fee = 0, api_key = None):
    if (len(mc_addresses) != len(amounts)):
        return "Addresses and amunts have differente lengths!"
    outputs = []
    for i in range(0, len(mc_addresses)):
        outputs += [{"mainchainAddress": mc_addresses[i], "value": amounts[i]}]

    j = {
        "outputs": outputs,
        "fee": fee
    }
    request = json.dumps(j)
    if (api_key != None):
        response = sidechainNode.transaction_withdrawCoins(request, api_key)
    else:
        response = sidechainNode.transaction_withdrawCoins(request)
    return response