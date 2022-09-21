# default value for a large withdrawal epoch length; such value has impacts on RAM and HD usage
# Note: the maximum withdrawal_epoch_length allowed is around 900, otherwise snark keys size check will fail
# because of too complex circuit from MC perspective.
# LARGE_WITHDRAWAL_EPOCH_LENGTH = 148
LARGE_WITHDRAWAL_EPOCH_LENGTH = 900

SC_CREATION_VERSION_0 = 0
SC_CREATION_VERSION_1 = 1

DEFAULT_API_KEY = "TopSecret"

"""
All information needed to bootstrap sidechain network within specified mainchain node.
The JSON representation is only for documentation.

SCCreationInfo: {
    "mc_node": Mainchain node
    "forward_amount": first Forward Transfer coin amount
    "withdrawal_epoch_length": length of Withdrawal Epoch
    "btr_data_length": size of scRequestData array for MBTRs. 0 if MBTRs are not supported at all.
    "sc_creation_version": sidechain version
    "cert_max_keys": defines the max number of Certificate proofs generation participants
    "cert_sig_threshold": the minimum set of the participants required for a valid proof creation
    "csw_enabled": true if the Ceased Sidechain Withdrawal should be enabled on the sidechain
}
"""
class SCCreationInfo(object):

    # Note: the maximum withdrawal_epoch_length allowed is around 900, otherwise snark keys size check will fail
    # because of too complex circuit from MC perspective.
    def __init__(self, mc_node, forward_amount=100, withdrawal_epoch_length=LARGE_WITHDRAWAL_EPOCH_LENGTH,
                 btr_data_length=0, sc_creation_version=SC_CREATION_VERSION_1,
                 cert_max_keys=7, cert_sig_threshold=5, csw_enabled=False):
        self.mc_node = mc_node
        self.forward_amount = forward_amount
        self.withdrawal_epoch_length = withdrawal_epoch_length
        self.btr_data_length = btr_data_length
        self.sc_creation_version = sc_creation_version
        self.cert_max_keys = cert_max_keys
        self.cert_sig_threshold = cert_sig_threshold
        self.csw_enabled = csw_enabled


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
Configuration that enables the possibility to restrict the forging phase
 to a specific list of forgers.
"""
class SCForgerConfiguration(object):
    def __init__(self, restrict_forgers = False, allowed_forgers = []):
        self.restrict_forgers = restrict_forgers
        self.allowed_forgers = []
        for forger in allowed_forgers:
            self.allowed_forgers.append('{ blockSignProposition = "'+forger[0]+'" NEW_LINE vrfPublicKey = "'+forger[1]+'" }')

"""
Configuration that enables the possibility to setup the latency on the sidechain network
"""
class LatencyConfig(object):
    def __init__(self, get_peer_spec=0, peer_spec=0, transaction=0, block=0, request_modifier_spec=0, modifiers_spec=0):
        self.get_peer_spec = get_peer_spec
        self.peer_spec = peer_spec
        self.transaction = transaction
        self.block = block
        self.request_modifier_spec = request_modifier_spec
        self.modifiers_spec = modifiers_spec

    def to_config(self):
        settings = ""
        settings += "get_peer_spec " + str(self.get_peer_spec)
        settings += " peer_spec " + str(self.peer_spec)
        settings += " transaction " + str(self.transaction)
        settings += " block " + str(self.block)
        settings += " request_modifier_spec " + str(self.request_modifier_spec)
        settings += " modifiers_spec " + str(self.modifiers_spec)

        return settings

    def default_string(self):
        settings = ""
        settings += "get_peer_spec " + str(0)
        settings += " peer_spec " + str(0)
        settings += " transaction " + str(0)
        settings += " block " + str(0)
        settings += " request_modifier_spec " + str(0)
        settings += " modifiers_spec " + str(0)
        return settings

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

    # Currently we have Cert Signature threshold snark proof with the max PK number = 7
    def __init__(self,
                 mc_connection_info=MCConnectionInfo(),
                 cert_submitter_enabled=True,
                 cert_signing_enabled=True,
                 submitter_private_keys_indexes=None,
                 max_connections=100,
                 automatic_fee_computation=True,
                 certificate_fee=0.0001,
                 forger_options=SCForgerConfiguration(),
                 mempool_max_size = 300,
                 mempool_min_fee_rate = 0,
                 api_key=DEFAULT_API_KEY,
                 max_fee=10000000,
                 block_rate=120,
                 latency_settings=0):
        if submitter_private_keys_indexes is None:
            submitter_private_keys_indexes = list(range(7))
        self.mc_connection_info = mc_connection_info
        self.cert_submitter_enabled = cert_submitter_enabled
        self.cert_signing_enabled = cert_signing_enabled
        self.submitter_private_keys_indexes = submitter_private_keys_indexes
        self.max_connections = max_connections
        self.automatic_fee_computation = automatic_fee_computation
        self.certificate_fee = certificate_fee
        self.forger_options = forger_options
        self.api_key = api_key
        self.max_fee = max_fee
        self.mempool_max_size = mempool_max_size
        self.mempool_min_fee_rate = mempool_min_fee_rate
        self.block_rate = block_rate
        self.latency_settings = latency_settings


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

# class SCMultiNetworkConfiguration(SCNetworkConfiguration):
#
#     def __init__(self, sc_creation_info, sc_nodes_configuration):
#         self.sc_creation_info = sc_creation_info
#         self.sc_nodes_configuration = sc_nodes_configuration


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

class AccountKey(object):
    def __init__(self, secret, proposition):
        self.secret = secret
        self.proposition = proposition

"""
A Schnorr key.
The JSON representation is only for documentation.

SchnorrAccount : {
    "schnorrSecret":
    "schnorrPublicKey": "a public key"
}
"""
class SchnorrAccount(object):

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
    "initial_cumulative_comm_tree_hash": CommTreeHash data for the genesis MC block
    "cert_keys_paths": an instance of ProofKeysPaths for certificate
    "csw_keys_paths": an instance of ProofKeysPaths for ceased sidechain withdrawal
}
"""
class SCBootstrapInfo(object):

    def __init__(self, sidechain_id, genesis_account, genesis_account_balance, mainchain_block_height,
                 sidechain_genesis_block_hex, pow_data, network, withdrawal_epoch_length, genesis_vrf_account,
                 certificate_proof_info, initial_cumulative_comm_tree_hash, cert_keys_paths, csw_keys_paths,
                 genesis_evm_account):
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
        self.initial_cumulative_comm_tree_hash = initial_cumulative_comm_tree_hash
        self.cert_keys_paths = cert_keys_paths
        self.csw_keys_paths = csw_keys_paths
        self.genesis_evm_account = genesis_evm_account


class ProofKeysPaths(object):

    def __init__(self, proving_key_path, verification_key_path):
        self.proving_key_path = proving_key_path
        self.verification_key_path = verification_key_path

