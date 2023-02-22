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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class SigningToolCommandProcessor extends CommandProcessor {

    public static final SchnorrSignatureSerializer SIGNATURE_SERIALIZER = SchnorrSignatureSerializer.getSerializer();
    public static final SidechainSecretsCompanion SECRETS_COMPANION = new SidechainSecretsCompanion(new HashMap<>());
    public static final String SCHNORR = "schnorr";

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
    // 2) <command name> <json argument>
    @Override
    protected Command parseCommand(String input) throws IOException {
        String[] inputData = input.trim().split(" ", 2);
        if (inputData.length == 0)
            throw new IOException(String.format("error: unrecognized input structure '%s'.%nSee 'help' for usage guideline.", input));

        ObjectMapper objectMapper = new ObjectMapper();
        // Check for command without arguments
        if (inputData.length == 1)
            return new Command(inputData[0], objectMapper.createObjectNode());

        String jsonData = inputData[1].trim();

        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(jsonData);
        } catch (Exception e) {
            throw new IOException(String.format("error: Invalid input data format '%s'. Json expected.%nSee 'help' for usage guideline.", jsonData));
        }

        return new Command(inputData[0], jsonNode);
    }

    @Override
    protected void printUsageMsg() {
        ObjectNode resJson = new ObjectMapper().createObjectNode();

        resJson.putIfAbsent("Usage", new ObjectMapper().createArrayNode()
                .add("From command line: <program name> <command name> [<json data>]"));
        resJson.putIfAbsent("Supported commands", new ObjectMapper().createArrayNode()
                .add("help")
                .add("createSignature <arguments>")
                .add("verifySignature <arguments>")
                .add("signMessage <arguments>")
                .add("validateMessage <arguments>")
                .add("privKeyToPubKey <arguments>")
        );

        printer.print(resJson.toString());
    }

    private void printCreateSignatureUsageMsg(String error) {
        ObjectNode resJson = new ObjectMapper().createObjectNode();

        resJson.put("error", error);
        resJson.put("Usage", "createSignature { \"message\": message, \"privateKey\": private_key, \"type\": \"string\" [\"schnorr\"] }");

        printer.print(resJson.toString());
    }

    private void createSignature(JsonNode json) {
        if (!json.has("message") || !json.get("message").isTextual()) {
            printCreateSignatureUsageMsg("message is not specified or has invalid format.");
            return;
        } else if (!json.has("privateKey") || !json.get("privateKey").isTextual()) {
            printCreateSignatureUsageMsg("privateKey is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual()) {
            printCreateSignatureUsageMsg("type is not specified or has invalid format.");
            return;
        }
        byte[] message = BytesUtils.fromHexString(json.get("message").asText());
        String privateKey = json.get("privateKey").asText();
        switch (json.get("type").asText()) {
            case SCHNORR:
                signMessageSchnorr(privateKey, message);
                break;
            default:
                printCreateSignatureUsageMsg("type " + json.get("type").asText() + " is not supported");
                break;
        }
    }

    private void printVerifySignatureUsageMsg(String error) {
        ObjectNode resJson = new ObjectMapper().createObjectNode();

        resJson.put("error", error);
        resJson.put("Usage", "verifySignature { \"message\": message, \"signature\": signature, \"publicKey\": public_key, \"type\": \"string\" [\"schnorr\"] }");

        printer.print(resJson.toString());
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
        } else if (!json.has("type") || !json.get("type").isTextual()) {
            printVerifySignatureUsageMsg("type is not specified or has invalid format.");
            return;
        }
        byte[] message = BytesUtils.fromHexString(json.get("message").asText());
        String signature = json.get("signature").asText();
        String publicKey = json.get("publicKey").asText();
        switch (json.get("type").asText()) {
            case SCHNORR:
                verifyMessageSchnorr(signature, publicKey, message);
                break;
            default:
                printVerifySignatureUsageMsg("type " + json.get("type").asText() + " is not supported");
                break;
        }
    }

    private void printSignMessageUsageMsg(String error) {
        ObjectNode resJson = new ObjectMapper().createObjectNode();

        resJson.put("error", error);
        resJson.put("Usage", "signMessage { \"message\": message, \"privateKey\": private_key, \"prefix\": prefix, \"type\": \"string\" [\"schnorr\"] }");

        printer.print(resJson.toString());
    }

    private void signMessage(JsonNode json) {
        if (!json.has("message") || !json.get("message").isTextual()) {
            printSignMessageUsageMsg("message is not specified or has invalid format.");
            return;
        } else if (!json.has("privateKey") || !json.get("privateKey").isTextual()) {
            printSignMessageUsageMsg("privateKey is not specified or has invalid format.");
            return;
        } else if (!json.has("prefix") || !json.get("prefix").isTextual() || json.get("prefix").asText().isEmpty()) {
            printSignMessageUsageMsg("prefix is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual()) {
            printSignMessageUsageMsg("type is not specified or has invalid format.");
            return;
        }
        String privateKey = json.get("privateKey").asText();
        byte[] message = BytesUtils.fromHexString(json.get("message").asText());
        byte[] prefix = json.get("prefix").asText().getBytes(StandardCharsets.UTF_8);
        byte[] prefixMessage = hash(concatenate(prefix, message));
        switch (json.get("type").asText()) {
            case SCHNORR:
                signMessageSchnorr(privateKey, prefixMessage);
                break;
            default:
                printSignMessageUsageMsg("type " + json.get("type").asText() + " is not supported");
                break;
        }
    }

    private void printValidateMessageUsageMsg(String error) {
        ObjectNode resJson = new ObjectMapper().createObjectNode();

        resJson.put("error", error);
        resJson.put("Usage", "validateMessage { \"message\": message, \"signature\": signature, \"publicKey\": public_key, \"prefix\": prefix, \"type\": \"string\" [\"schnorr\"] }");

        printer.print(resJson.toString());
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
        } else if (!json.has("prefix") || !json.get("prefix").isTextual() || json.get("prefix").asText().isEmpty()) {
            printValidateMessageUsageMsg("prefix is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual()) {
            printValidateMessageUsageMsg("type is not specified or has invalid format.");
            return;
        }
        byte[] message = BytesUtils.fromHexString(json.get("message").asText());
        String signature = json.get("signature").asText();
        String publicKey = json.get("publicKey").asText();
        byte[] prefix = json.get("prefix").asText().getBytes(StandardCharsets.UTF_8);
        byte[] prefixMessage = hash(concatenate(prefix, message));
        switch (json.get("type").asText()) {
            case SCHNORR:
                verifyMessageSchnorr(signature, publicKey, prefixMessage);
                break;
            default:
                printValidateMessageUsageMsg("type " + json.get("type").asText() + " is not supported");
                break;
        }
    }

    private void printPrivKeyToPubKeyUsageMsg(String error) {
        ObjectNode resJson = new ObjectMapper().createObjectNode();

        resJson.put("error", error);
        resJson.put("Usage", "privKeyToPubKey { \"privateKey\": private_key, \"type\": \"string\" [\"schnorr\"] }");

        printer.print(resJson.toString());
    }

    private void privKeyToPubKey(JsonNode json) {
        if (!json.has("privateKey") || !json.get("privateKey").isTextual()) {
            printPrivKeyToPubKeyUsageMsg("privateKey is not specified or has invalid format.");
            return;
        } else if (!json.has("type") || !json.get("type").isTextual()) {
            printPrivKeyToPubKeyUsageMsg("type is not specified or has invalid format.");
            return;
        }
        String privateKey = json.get("privateKey").asText();
        switch (json.get("type").asText()) {
            case SCHNORR:
                Secret secret;
                try {
                    secret = SECRETS_COMPANION.parseBytes(BytesUtils.fromHexString(privateKey));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unable to parse privateKey bytes: " + e.getMessage());
                }
                if (secret.secretTypeId() != SecretsIdsEnum.SchnorrSecretKeyId.id())
                    throw new RuntimeException("Provided privateKey type is not supported : " + secret.secretTypeId());
                SchnorrSecret secretKey = (SchnorrSecret) secret;
                byte[] publicKey = SchnorrPropositionSerializer.getSerializer().toBytes(secretKey.publicImage());

                ObjectNode resJson = new ObjectMapper().createObjectNode();
                resJson.put("publicKey", BytesUtils.toHexString(publicKey));
                printer.print(resJson.toString());
                break;
            default:
                printPrivKeyToPubKeyUsageMsg("type " + json.get("type").asText() + " is not supported");
                break;
        }
    }

    private void signMessageSchnorr(String privateKey, byte[] message) {
        Secret secret;
        try {
            secret = SECRETS_COMPANION.parseBytes(BytesUtils.fromHexString(privateKey));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to parse privateKey bytes: " + e.getMessage());
        }
        if (secret.secretTypeId() != SecretsIdsEnum.SchnorrSecretKeyId.id())
            throw new RuntimeException("Provided privateKey type is not supported : " + secret.secretTypeId());
        SchnorrSecret secretKey = (SchnorrSecret) secret;

        SchnorrProof signature = secretKey.sign(message);

        byte[] signatureBytes = SIGNATURE_SERIALIZER.toBytes(signature);

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("signature", BytesUtils.toHexString(signatureBytes));

        printer.print(resJson.toString());
    }

    private void verifyMessageSchnorr(String signature, String publicKey, byte[] message) {
        SchnorrProof proof;
        SchnorrProposition proposition;
        try {
            proof = SchnorrSignatureSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(signature));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to parse signature bytes: " + e.getMessage());
        }
        try {
            proposition = SchnorrPropositionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(publicKey));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to parse publicKey bytes: " + e.getMessage());
        }
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
