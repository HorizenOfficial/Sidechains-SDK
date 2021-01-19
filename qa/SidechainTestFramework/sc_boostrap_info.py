"""
All information needed to bootstrap sidechain network within specified mainchain node.
The JSON representation is only for documentation.

SCCreationInfo: {
    "mc_node":
    "sc_id":
    "forward_amout":
    "withdrawal_epoch_length":
}
"""
class SCCreationInfo(object):

    def __init__(self, mc_node, forward_amount=100, withdrawal_epoch_length=1000):
        self.mc_node = mc_node
        self.forward_amount = forward_amount
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
Information needed to start a sidechain node connected to specific mainchain node.
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

    def __init__(self, mc_connection_info=MCConnectionInfo(), cert_submitter_enabled=True):
        self.mc_connection_info = mc_connection_info
        self.cert_submitter_enabled = cert_submitter_enabled


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
An account.
The JSON representation is only for documentation.

Account: {
    "secret":
    "publicKey": "a public key"
}
"""
class Account(object):

    def __init__(self, secret, publicKey):
        self.secret = secret
        self.publicKey = publicKey

"""
A Vrf key.
The JSON representation is only for documentation.

VrfAccount : {
    "vrfSecret":
    "vrfPublicKey": "a public key"
}
"""
class VrfAccount(object):

    def __init__(self, secret, publicKey):
        self.secret = secret
        self.publicKey = publicKey

"""
Withdrawal certificate proof info  data .
The JSON representation is only for documentation.

CertificateProofInfo : {
    "threshold":
    "genSysConstant":"5e7b..0000"
    "verificationKey":"caa..000"
    "schnorr_secrets": secret key in byte hex representation
    "schnorr_public_keys": public key in byte hex representation
}
"""
class CertificateProofInfo(object):

    def __init__(self, threshold, genSysConstant, verificationKey, schnorr_secrets = [], schnorr_public_keys = []):
        self.threshold = threshold
        self.genSysConstant = genSysConstant
        self.verificationKey = verificationKey
        self.schnorr_secrets = schnorr_secrets
        self.schnorr_public_keys = schnorr_public_keys
"""
Information about sidechain network already bootstrapped.
The JSON representation is only for documentation.

SCBootstrapInfo: {
    "sidechain_id":
    "genesis_account": an instance of Account
    "genesis_account_balance":
    "mainchain_block_height": the height of the mainchain block at which the sidechain has been created (useful for future checks of mainchain block reference inclusion)
    "sidechain_genesis_block_hex":
    "pow_data":
    "network":
    "withdrawal_epoch_length":
    "genesis_vrf_account": an instance of VrfAccount
    "certificate_proof_info": an instance of CertificateProofInfo
}
"""
class SCBootstrapInfo(object):

    def __init__(self, sidechain_id, genesis_account, genesis_account_balance, mainchain_block_height,
                 sidechain_genesis_block_hex, pow_data, network, withdrawal_epoch_length, genesis_vrf_account, certificate_proof_info):
        self.sidechain_id = sidechain_id
        self.genesis_account = genesis_account
        self.genesis_account_balance = genesis_account_balance
        self.mainchain_block_height = mainchain_block_height
        self.sidechain_genesis_block_hex = sidechain_genesis_block_hex
        self.pow_data = pow_data
        self.network = network
        self.withdrawal_epoch_length = withdrawal_epoch_length
        self.genesis_vrf_account = genesis_vrf_account
        self.certificate_proof_info = certificate_proof_info