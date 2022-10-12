package com.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.companion.SidechainSecretsCompanion;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.proof.SchnorrProof;
import com.horizen.proof.SchnorrSignatureSerializer;
import com.horizen.proposition.SchnorrProposition;
import com.horizen.proposition.SchnorrPropositionSerializer;
import com.horizen.secret.SchnorrSecret;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretsIdsEnum;
import com.horizen.tools.utils.Command;
import com.horizen.tools.utils.CommandProcessor;
import com.horizen.tools.utils.MessagePrinter;
import com.horizen.utils.BytesUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

public class SigningToolCommandProcessor extends CommandProcessor {

    public static final SchnorrSignatureSerializer SIGNATURE_SERIALIZER = SchnorrSignatureSerializer.getSerializer();
    public static final SidechainSecretsCompanion SECRETS_COMPANION = new SidechainSecretsCompanion(new HashMap<>());

    public SigningToolCommandProcessor(MessagePrinter printer) {
        super(printer);
    }

    @Override
    public void processCommand(String input) throws IOException {
        Command command = parseCommand(input);
        try {
            switch (command.name()) {
                case "help":
                    printUsageMsg();
                    break;
                case "createSignature":
                    createSignature(command.data());
                    break;
                case "verifySignature":
                    verifySignature(command.data());
                    break;
                case "signMessage":
                    signMessage(command.data());
                    break;
                case "validateMessage":
                    validateMessage(command.data());
                    break;
                case "privKeyToPubKey":
                    privKeyToPubKey(command.data());
                    break;
                default:
                    printUnsupportedCommandMsg(command.name());
            }
        } catch (Exception e) {
            ObjectNode resJson = new ObjectMapper().createObjectNode();
            resJson.put("error", e.getMessage());

            printer.print(resJson.toString());
        }
    }

    // Command structure is:
    // 1) <command name>
    // 1) <command name> <json argument>
    // 2) <command name> -f <path to file with json argument>
    @Override
    protected Command parseCommand(String input) throws IOException {
        String[] inputData = input.trim().split(" ", 2);
        if (inputData.length == 0)
            throw new IOException(String.format("Error: unrecognized input structure '%s'.%nSee 'help' for usage guideline.", input));

        ObjectMapper objectMapper = new ObjectMapper();
        // Check for command without arguments
        if (inputData.length == 1)
            return new Command(inputData[0], objectMapper.createObjectNode());

        String jsonData;
        String commandArguments = inputData[1].trim();
        // Check for file flag
        if (commandArguments.startsWith("-f ")) {
            // Remove '-f', possible around whitespaces and/or quotes
            String filePath = commandArguments.replaceAll("^-f\\s*\"*|\"$", "");
            // Try to open and read data from file
            try (
                    FileReader file = new FileReader(filePath);
                    BufferedReader reader = new BufferedReader(file)
            ) {
                jsonData = reader.readLine();
            } catch (FileNotFoundException e) {
                throw new IOException(String.format("Error: Input data file '%s' not found.%nSee 'help' for usage guideline.", filePath));
            }
        } else {
            jsonData = commandArguments;
        }

        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(jsonData);
        } catch (Exception e) {
            throw new IOException(String.format("Error: Invalid input data format '%s'. Json expected.%nSee 'help' for usage guideline.", jsonData));
        }

        return new Command(inputData[0], jsonNode);
    }

    @Override
    protected void printUsageMsg() {
        printer.print(
                "Usage:\n" +
                        "\tFrom command line: <program name> <command name> [<json data>]\n" +
                        "\tFor interactive mode: <command name> [<json data>]\n" +
                        "\tRead command arguments from file: <command name> -f <path to file with json data>\n" +
                        "\n" +
                        "Supported commands:\n" +
                        "\thelp\n" +
                        "\tcreateSignature <arguments>\n" +
                        "\tverifySignature <arguments>\n" +
                        "\tsignMessage <arguments>\n" +
                        "\tvalidateMessage <arguments>\n" +
                        "\tprivKeyToPubKey <arguments>\n" +
                        "\texit\n"
        );
    }

    private void printCreateSignatureUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tcreateSignature { \"message\": message, \"privateKey\": private_key, \"type\": \"string\" [\"schnorr\"] }");
    }

    private void createSignature(JsonNode json) {
        if (!json.has("message") || !json.get("message").isTextual()) {
            printCreateSignatureUsageMsg("message is not specified or has invalid format.");
            return;
        } else if (!json.has("privateKey") || !json.get("privateKey").isTextual()) {
            printCreateSignatureUsageMsg("privateKey is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual() || !Objects.equals(json.get("type").asText(), "schnorr")) {
            printCreateSignatureUsageMsg("type is not specified or has invalid format.");
            return;
        }
        byte[] message = json.get("message").asText().getBytes();
        String privateKey = json.get("privateKey").asText();
        signMessage(privateKey, message);
    }

    private void printVerifySignatureUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tverifySignature { \"message\": message, \"signature\": signature, \"publicKey\": public_key, \"type\": \"string\" [\"schnorr\"] }");
    }

    private void verifySignature(JsonNode json) {
        if (!json.has("message") || !json.get("message").isTextual()) {
            printVerifySignatureUsageMsg("message is not specified or has invalid format.");
            return;
        } else if (!json.has("signature") || !json.get("signature").isTextual()) {
            printVerifySignatureUsageMsg("signature is not specified or has invalid format.");
            return;
        } else if (!json.has("publicKey") || !json.get("publicKey").isTextual()) {
            printVerifySignatureUsageMsg("publicKey is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual() || !Objects.equals(json.get("type").asText(), "schnorr")) {
            printVerifySignatureUsageMsg("type is not specified or has invalid format.");
            return;
        }
        byte[] message = json.get("message").asText().getBytes();
        String signature = json.get("signature").asText();
        String publicKey = json.get("publicKey").asText();
        verifyMessage(signature, publicKey, message);
    }

    private void printSignMessageUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tsignMessage { \"message\": message, \"privateKey\": private_key, \"prefix\": prefix, \"type\": \"string\" [\"schnorr\"] }");
    }

    private void signMessage(JsonNode json) {
        if (!json.has("message") || !json.get("message").isTextual()) {
            printSignMessageUsageMsg("message is not specified or has invalid format.");
            return;
        } else if (!json.has("privateKey") || !json.get("privateKey").isTextual()) {
            printSignMessageUsageMsg("privateKey is not specified or has invalid format.");
            return;
        } else if (!json.has("prefix") || !json.get("prefix").isTextual()) {
            printSignMessageUsageMsg("prefix is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual() || !Objects.equals(json.get("type").asText(), "schnorr")) {
            printSignMessageUsageMsg("type is not specified or has invalid format.");
            return;
        }
        String privateKey = json.get("privateKey").asText();
        byte[] message = json.get("message").asText().getBytes();
        byte[] prefix = json.get("prefix").asText().getBytes();
        byte[] prefixMessage = hash(concatenate(prefix, message));

        signMessage(privateKey, prefixMessage);
    }

    private void printValidateMessageUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tvalidateMessage { \"message\": message, \"signature\": signature, \"publicKey\": public_key, \"prefix\": prefix, \"type\": \"string\" [\"schnorr\"] }");
    }

    private void validateMessage(JsonNode json) {
        if (!json.has("message") || !json.get("message").isTextual()) {
            printValidateMessageUsageMsg("message is not specified or has invalid format.");
            return;
        } else if (!json.has("signature") || !json.get("signature").isTextual()) {
            printValidateMessageUsageMsg("signature is not specified or has invalid format.");
            return;
        } else if (!json.has("publicKey") || !json.get("publicKey").isTextual()) {
            printValidateMessageUsageMsg("publicKey is not specified or has invalid format.");
            return;
        } else if (!json.has("prefix") || !json.get("prefix").isTextual()) {
            printValidateMessageUsageMsg("prefix is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual() || !Objects.equals(json.get("type").asText(), "schnorr")) {
            printValidateMessageUsageMsg("type is not specified or has invalid format.");
            return;
        }
        byte[] message = json.get("message").asText().getBytes();
        String signature = json.get("signature").asText();
        String publicKey = json.get("publicKey").asText();
        byte[] prefix = json.get("prefix").asText().getBytes();
        byte[] prefixMessage = hash(concatenate(prefix, message));

        verifyMessage(signature, publicKey, prefixMessage);
    }

    private void printPrivKeyToPubKeyUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tprivKeyToPubKey { \"privateKey\": private_key, \"type\": \"string\" [\"schnorr\"] }");
    }

    private void privKeyToPubkey(JsonNode json) {
        if (!json.has("privateKey") || !json.get("privateKey").isTextual()) {
            printPrivKeyToPubKeyUsageMsg("privateKey is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual() || !Objects.equals(json.get("type").asText(), "schnorr")) {
            printPrivKeyToPubKeyUsageMsg("type is not specified or has invalid format.");
            return;
        }

        String privateKey = json.get("privateKey").asText();
        Secret secret = SECRETS_COMPANION.parseBytes(BytesUtils.fromHexString(privateKey));
        assert secret.secretTypeId() == SecretsIdsEnum.SchnorrSecretKeyId.id();
        SchnorrSecret secretKey = (SchnorrSecret) secret;
        byte[] publicKey = SchnorrPropositionSerializer.getSerializer().toBytes(secretKey.publicImage());

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("publicKey", BytesUtils.toHexString(publicKey));
        printer.print(resJson.toString());
    }

    private void signMessage(String privateKey, byte[] message) {
        Secret secret = SECRETS_COMPANION.parseBytes(BytesUtils.fromHexString(privateKey));
        assert secret.secretTypeId() == SecretsIdsEnum.SchnorrSecretKeyId.id();
        SchnorrSecret secretKey = (SchnorrSecret) secret;

        SchnorrProof signature = secretKey.sign(message);

        byte[] signatureBytes = SIGNATURE_SERIALIZER.toBytes(signature);

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("signature", BytesUtils.toHexString(signatureBytes));

        printer.print(resJson.toString());
    }

    private void verifyMessage(String signature, String publicKey, byte[] message) {
        SchnorrProof proof = SchnorrSignatureSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(signature));
        SchnorrProposition proposition = SchnorrPropositionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(publicKey));

        boolean res = proposition.verify(message, proof);

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("valid", res);

        printer.print(resJson.toString());
    }

    public byte[] concatenate(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;

        byte[] res = new byte[aLen + bLen];
        System.arraycopy(a, 0, res, 0, aLen);
        System.arraycopy(b, 0, res, aLen, bLen);

        return res;
    }

    private byte[] hash(byte[] input) {
        PoseidonHash digest = PoseidonHash.getInstanceConstantLength(1);
        FieldElement fieldElement = FieldElement.deserialize(input);
        digest.update(fieldElement);
        FieldElement resElement = digest.finalizeHash();
        byte[] resBytes = resElement.serializeFieldElement();
        digest.freePoseidonHash();
        fieldElement.freeFieldElement();
        resElement.freeFieldElement();
        return resBytes;
    }
}
