"""
A mainchain node uses SCCreationInfo to create a sidechain.
The JSON representation is only for documentation.

SCCreationInfo: {
    "mc_node":
    "sc_id":
    "forward_amout":
    "withdrawal_epoch_length":
}
"""
class SCCreationInfo(object):

    def __init__(self, mc_node, sidechain_id="".zfill(64), forward_amout=100, withdrawal_epoch_length=1000):
        self.mc_node = mc_node
        self.sidechain_id = sidechain_id
        self.forward_amout = forward_amout
        self.withdrawal_epoch_length = withdrawal_epoch_length


"""
Sidechain websocket configuration to be added inside the configuration file.
The JSON representation is only for documentation.

MCConnectionInfo: {
    "address":
    "connectionTimeout":
    "reconnectionDelay":
    "reconnectionMaxAttempts":
}
"""
class MCConnectionInfo(object):

    def __init__(self, address="ws://localhost:8888", connectionTimeout=100, reconnectionDelay=1,
                 reconnectionMaxAttempts=1):
        self.address = address
        self.connectionTimeout = connectionTimeout
        self.reconnectionDelay = reconnectionDelay
        self.reconnectionMaxAttempts = reconnectionMaxAttempts


"""
All information needed to bootstrap one sidechain node connected to a mainchain node.
The JSON representation is only for documentation.

SCNodeConfiguration: {
    "mc_connection_info":{
        "address":
        "connectionTimeout":
        "reconnectionDelay":
        "reconnectionMaxAttempts":
    }
}
"""
class SCNodeConfiguration(object):

    def __init__(self, mc_connection_info=MCConnectionInfo()):
        self.mc_connection_info = mc_connection_info


"""
The full network of many sidechain nodes connected to many mainchain nodes.
The JSON representation is only for documentation.

SCNetworkConfiguration: {
    SCCreationInfo, 
    [
        SCNodeConfiguration_0
        ...
        SCNodeConfiguration_i
    ]
}
"""
class SCNetworkConfiguration(object):

    def __init__(self, sc_creation_info, *sc_nodes_configuration):
        self.sc_creation_info = sc_creation_info
        self.sc_nodes_configuration = sc_nodes_configuration

"""
Information a sidechain node already bootstrapped.
The JSON representation is only for documentation.

SCBootstrapInfo: {
    "sidechain_id":
    "genesis_account": a tuple [secret, public key]
    "wallet_balance":
    "mainchain_block_height": the height of the mainchain block at which the sidechain has been created (useful for future checks of mainchain block reference inclusion)
    "sidechain_genesis_block_hex":
    "pow_data":
    "network":
}
"""
class SCBootstrapInfo(object):

    def __init__(self, sidechain_id, genesis_account, wallet_balance, mainchain_block_height, sidechain_genesis_block_hex, pow_data, network):
        self.sidechain_id = sidechain_id
        self.genesis_account = genesis_account
        self.wallet_balance = wallet_balance
        self.mainchain_block_height = mainchain_block_height
        self.sidechain_genesis_block_hex = sidechain_genesis_block_hex
        self.pow_data = pow_data
        self.network = network
