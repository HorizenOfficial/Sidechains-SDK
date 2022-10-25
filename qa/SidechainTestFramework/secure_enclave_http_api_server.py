import logging
import multiprocessing
import os
import subprocess
import threading

from flask import Flask, request, json


class SecureEnclaveApiServer(object):
    app = Flask(__name__)

    @app.route('/signMessage', methods=['POST'])
    def sign_message(self):
        content = request.json
        pk = content['publicKey']
        index = self.schnorr_public_keys.index(pk)
        sk = self.schnorr_secrets[index]
        content.pop('publicKey', None)
        content['privateKey'] = sk

        result = launch_signing_tool(content)
        return result

    def __init__(self, schnorr_secrets=[], schnorr_public_keys=[]):
        self.thread = None
        self.schnorr_secrets = schnorr_secrets
        self.schnorr_public_keys = schnorr_public_keys

    def start(self):
        self.thread = multiprocessing.Process(target=lambda: self.app.run(debug=False))
        self.thread.start()

    def stop(self):
        self.thread.terminate()


def launch_signing_tool(json_parameters):
    json_param = json.dumps(json_parameters)

    java_ps = subprocess.Popen(["java", "-jar",
                                os.getenv("SIDECHAIN_SDK", "..")
                                + "/tools/signingtool/target/sidechains-sdk-signingtools-0.5.0-SNAPSHOT.jar",
                                "createSignature", json_param], stdout=subprocess.PIPE)
    db_tool_output = java_ps.communicate()[0]
    try:
        jsone_node = json.loads(db_tool_output)
        return jsone_node
    except ValueError:
        logging.info("Signing tool error occurred for command= {}\nparams: {}\nError: {}\n"
                     .format("createSignature", json_param, db_tool_output.decode()))
        raise Exception("Signing tool error occurred")
