package com.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.cryptolibprovider.SchnorrFunctionsImplZendoo;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSecretKey;
import com.horizen.tools.utils.Command;
import com.horizen.tools.utils.CommandProcessor;
import com.horizen.tools.utils.MessagePrinter;
import scorex.util.encode.Base64;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;

public class SigningToolCommandProcessor extends CommandProcessor {

    private final SchnorrFunctionsImplZendoo schnorrFunctions = new SchnorrFunctionsImplZendoo();

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
                case "privKeyToPubkey":
                    privKeyToPubkey(command.data());
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
                        "\tprivKeyToPubkey <arguments>\n" +
                        "\texit\n"
        );
    }

    private void printCreateSignatureUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tcreateSignature { \"message\": message, \"privateKey\": private_key, \"type\": \"string\" [\"schnorr\"] }");
    }

    private void createSignature(JsonNode json) throws Exception {
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
        byte[] privateKey = Base64.decode(json.get("privateKey").asText()).get();
        byte[] publicKey = privateToPublicKey(privateKey);
        byte[] message = json.get("message").asText().getBytes();

        byte[] signatureBytes = schnorrFunctions.sign(privateKey, publicKey, message);
        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("signature", signatureBytes);

        printer.print(resJson.toString());
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
        byte[] signature = Base64.decode(json.get("signature").asText()).get();
        byte[] publicKey = Base64.decode(json.get("publicKey").asText()).get();

        boolean res = schnorrFunctions.verify(message, publicKey, signature);

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("valid", res);

        printer.print(resJson.toString());
    }


    private void printSignMessageUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tsignMessage { \"message\": message, \"privateKey\": private_key, \"prefix\": prefix, \"type\": \"string\" [\"schnorr\"] }");
    }

    private void signMessage(JsonNode json) throws Exception {
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
        byte[] privateKey = Base64.decode(json.get("privateKey").asText()).get();
        byte[] publicKey = privateToPublicKey(privateKey);
        byte[] message = json.get("message").asText().getBytes();
        byte[] prefix = json.get("prefix").asText().getBytes();
        byte[] prefixMessage = hash(concatenate(prefix, message));

        byte[] signatureBytes = schnorrFunctions.sign(privateKey, publicKey, prefixMessage);
        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("signature", signatureBytes);

        printer.print(resJson.toString());
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
        byte[] signature = Base64.decode(json.get("signature").asText()).get();
        byte[] publicKey = Base64.decode(json.get("publicKey").asText()).get();
        byte[] prefix = json.get("prefix").asText().getBytes();
        byte[] prefixMessage = hash(concatenate(prefix, message));

        boolean res = schnorrFunctions.verify(prefixMessage, publicKey, signature);

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("valid", res);

        printer.print(resJson.toString());
    }

    private void printPrivKeyToPubkeyUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tprivKeyToPubkey { \"privateKey\": private_key, \"type\": \"string\" [\"schnorr\"] }");
    }

    private void privKeyToPubkey(JsonNode json) throws Exception {
        if (!json.has("privateKey") || !json.get("privateKey").isTextual()) {
            printPrivKeyToPubkeyUsageMsg("privateKey is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual() || !Objects.equals(json.get("type").asText(), "schnorr")) {
            printPrivKeyToPubkeyUsageMsg("type is not specified or has invalid format.");
            return;
        }

        byte[] privateKey = Base64.decode(json.get("privateKey").asText()).get();
        byte[] publicKey = privateToPublicKey(privateKey);

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("publicKey", publicKey);
        printer.print(resJson.toString());
    }

    private byte[] privateToPublicKey(byte[] privateKey) throws Exception {
        try (SchnorrSecretKey secretKey = SchnorrSecretKey.deserialize(privateKey);
             SchnorrPublicKey publicKey = secretKey.getPublicKey())
        {
            return publicKey.serializePublicKey();
        }
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
