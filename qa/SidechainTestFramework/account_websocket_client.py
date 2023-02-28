from websocket import create_connection
import time
import json

class AccountWebsocketClient():
    # Websocket account request
    SUBSCRIBE_REQUEST = "eth_subscribe"
    UNSUBSCRIBE_REQUEST = "eth_unsubscribe"

    # Websocket account response
    SUBSCRIBE_RESPONSE = "eth_subscription"
    UNSUBSCRIBE_RESPONSE = "eth_unsubscription"


    # Websocket account subscriptions
    NEW_HEADS_SUBSCRIPTION = "newHeads"
    NEW_PENDING_TRANSACTIONS_SUBSCRIPTION = "newPendingTransactions"
    LOGS_SUBSCRIPTION = "logs"

    def create_connection(self, url):
        return create_connection(url)

    def sendMessage(self, ws, id, request, params):
        wsRequest = {"jsonrpc": "2.0","id": id, "method": request, "params": params}
        ws.send(json.dumps(wsRequest))
        timeout = 500
        for x in range(1, timeout):
            results = ws.recv()
            if len(results)==0:
                time.sleep(1)
            else:
                break
        return results
