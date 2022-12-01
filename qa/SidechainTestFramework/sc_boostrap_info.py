# default value for a large withdrawal epoch length; such value has impacts on RAM and HD usage
# Note: the maximum withdrawal_epoch_length allowed is around 900, otherwise snark keys size check will fail
# because of too complex circuit from MC perspective.
# LARGE_WITHDRAWAL_EPOCH_LENGTH = 148
LARGE_WITHDRAWAL_EPOCH_LENGTH = 900

SC_CREATION_VERSION_0 = 0
SC_CREATION_VERSION_1 = 1
SC_CREATION_VERSION_2 = 2

NO_KEY_ROTATION_CIRCUIT = 'NaiveThresholdSignatureCircuit'
KEY_ROTATION_CIRCUIT = 'NaiveThresholdSignatureCircuitWithKeyRotation'

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
    "circuit_type" is the type of circuit for certificate submitter
}
"""


class SCCreationInfo(object):

    # Note: the maximum withdrawal_epoch_length allowed is around 900, otherwise snark keys size check will fail
    # because of too complex circuit from MC perspective.
    def __init__(self, mc_node, forward_amount=100, withdrawal_epoch_length=LARGE_WITHDRAWAL_EPOCH_LENGTH,
                 btr_data_length=0, sc_creation_version=SC_CREATION_VERSION_1,
                 cert_max_keys=7, cert_sig_threshold=5, csw_enabled=False, is_non_ceasing=False,
                 circuit_type=NO_KEY_ROTATION_CIRCUIT):
        self.mc_node = mc_node
        self.forward_amount = forward_amount
        self.withdrawal_epoch_length = withdrawal_epoch_length
        self.btr_data_length = btr_data_length
        self.sc_creation_version = sc_creation_version
        self.cert_max_keys = cert_max_keys
        self.cert_sig_threshold = cert_sig_threshold
        self.csw_enabled = csw_enabled
        self.non_ceasing = is_non_ceasing

        if csw_enabled and is_non_ceasing:
            raise RuntimeError('Cannot enable CSW and Non-ceasing options simultaneously.')

        if sc_creation_version != SC_CREATION_VERSION_2 and is_non_ceasing:
            raise RuntimeError('Cannot initialize non-ceasing sidechain with version different '
                               'from ' + SC_CREATION_VERSION_2 + '. Found ' + sc_creation_version)
        self.circuit_type = circuit_type


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
    def __init__(self, restrict_forgers=False, allowed_forgers=[]):
        self.restrict_forgers = restrict_forgers
        self.allowed_forgers = []
        for forger in allowed_forgers:
            self.allowed_forgers.append(
                '{ blockSignProposition = "' + forger[0] + '" NEW_LINE vrfPublicKey = "' + forger[1] + '" }')


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
                 mempool_max_size=300,
                 mempool_min_fee_rate=0,
                 api_key=DEFAULT_API_KEY,
                 max_fee=10000000,
                 initial_private_keys=[],
                 initial_signing_private_keys=[],
                 remote_keys_manager_enabled=False):
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
        self.initial_private_keys = initial_private_keys
        self.initial_signing_private_keys = initial_signing_private_keys
        self.remote_keys_manager_enabled = remote_keys_manager_enabled

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
    "schnorr_signers_secrets": certificate signer secret keys in byte hex representation
    "schnorr_masters_secrets": certificate master keys in byte hex representation (only for key rotation circuit)
    "public_signing_keys": public keys in byte hex representation
    "public_master_keys": public keys in byte hex representation (only for key rotation circuit)
}
"""


class CertificateProofInfo(object):

    def __init__(self, threshold, genSysConstant, verificationKey, schnorr_signers_secrets=[], public_signing_keys=[],
                 schnorr_masters_secrets=[], public_master_keys=[]):
        self.threshold = threshold
        self.genSysConstant = genSysConstant
        self.verificationKey = verificationKey
        self.schnorr_signers_secrets = schnorr_signers_secrets
        self.schnorr_masters_secrets = schnorr_masters_secrets
        self.public_signing_keys = public_signing_keys
        self.public_master_keys = public_master_keys


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
                 certificate_proof_info, initial_cumulative_comm_tree_hash, is_non_ceasing, cert_keys_paths, csw_keys_paths,
                 circuit_type):
        self.sidechain_id = sidechain_id
        self.genesis_account = genesis_account
        self.genesis_account_balance = genesis_account_balance
        self.mainchain_block_height = mainchain_block_height
        self.sidechain_genesis_block_hex = sidechain_genesis_block_hex
        self.pow_data = pow_data
        self.network = network
        self.is_non_ceasing = is_non_ceasing
        self.withdrawal_epoch_length = withdrawal_epoch_length
        self.genesis_vrf_account = genesis_vrf_account
        self.certificate_proof_info = certificate_proof_info
        self.initial_cumulative_comm_tree_hash = initial_cumulative_comm_tree_hash
        self.cert_keys_paths = cert_keys_paths
        self.csw_keys_paths = csw_keys_paths
        self.circuit_type = circuit_type


class ProofKeysPaths(object):

    def __init__(self, proving_key_path, verification_key_path):
        self.proving_key_path = proving_key_path
        self.verification_key_path = verification_key_path
