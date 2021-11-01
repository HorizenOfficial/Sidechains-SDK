#executes a wallet/balance call
def http_wallet_balance(sidechainNode):
      response = sidechainNode.wallet_coinsBalance()
      return response['result']['balance']



