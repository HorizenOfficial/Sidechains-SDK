import json

#executes a wallet/balance call on an account sidechain
def http_wallet_balance(sidechainNode, evm_address):
      j = {"address": str(evm_address)}
      balance_request = json.dumps(j)

      # balance is in wei
      return sidechainNode.wallet_getBalance(balance_request)["result"]["balance"]



