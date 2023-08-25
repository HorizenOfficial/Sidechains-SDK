import logging
import multiprocessing
import os
import subprocess

from flask import Flask, request, json

from test_framework.util import assert_equal


class SecureEnclaveApiServer(object):

    def start(self):
        # REQUEST
        # {
        #     "message": message,
        #     "publicKey": publicKey, (optional)
        #     "privateKey": privateKey, (optional)
        #     "type": "string"["schnorr"]
        # }
        # Either publicKey or privateKey must be specified.
        #
        # RESPONSE
        # {
        #     "signature": signature,
        #     "error": "description" | null
        # }
        @self.app.route('/api/v1/createSignature', methods=['POST'])
        def sign_message():
            content = json.loads(request.data)
            if "akka-http" in request.headers['User-Agent']:
                assert_equal("application/json", request.headers['Content-Type'])
                assert_equal("application/json", request.headers['Accept'])
            logging.info("SecureEnclaveApiServer /api/v1/createSignature received request " + str(content))
            if 'privateKey' not in content:
                pk = content['publicKey']
                index = self.schnorr_public_keys.index(pk)
                sk = self.schnorr_secrets[index]
                content['privateKey'] = sk
                content.pop('publicKey', None)

            result = launch_signing_tool(content)
            logging.info("SecureEnclaveApiServer /api/v1/createSignature result " + str(result))
            return result

        # REQUEST
        # {
        #     "type": "string"["schnorr"](optional)
        # }
        # RESPONSE
        # {
        #     "keys": [
        #         {
        #             "publicKey": "publicKey",
        #             "type": "string"["schnorr"]
        #         }
        #     ],
        #     "error": "description" | null
        # }
        @self.app.route('/api/v1/listKeys', methods=['POST'])
        def list_keys():
            logging.info("SecureEnclaveApiServer /api/v1/listKeys received request")
            keys = []
            for key in self.schnorr_public_keys:
                keys.append({"publicKey": key, "type": "schnorr"})

            result = json.dumps({"keys": keys})
            logging.info("SecureEnclaveApiServer /api/v1/listKeys result" + result)
            return result

        multiprocessing.Process(daemon=True, target=lambda: self.app.run(host=self.host, port=self.port, debug=False,
                                                                         use_reloader=False)).start()

    def __init__(self, schnorr_secrets=None, schnorr_public_keys=None, host="127.0.0.1", port=5000):
        if schnorr_public_keys is None:
            schnorr_public_keys = []
        if schnorr_secrets is None:
            schnorr_secrets = []
        self.app = Flask(__name__)
        self.schnorr_secrets = schnorr_secrets
        self.schnorr_public_keys = schnorr_public_keys
        self.host = host
        self.port = port


def launch_signing_tool(json_parameters):
    json_param = json.dumps(json_parameters)

    java_ps = subprocess.Popen(["java", "-jar",
                                os.getenv("SIDECHAIN_SDK", "..")
                                + "/tools/signingtool/target/sidechains-sdk-signingtools-0.6.9-SNAPSHOT.jar",
                                "createSignature", json_param], stdout=subprocess.PIPE)
    db_tool_output = java_ps.communicate()[0]
    try:
        jsone_node = json.loads(db_tool_output)
        return jsone_node
    except ValueError:
        logging.error("Signing tool error occurred for command= {}\nparams: {}\nError: {}\n"
                      .format("createSignature", json_param, db_tool_output.decode()))
        raise Exception("Signing tool error occurred")
