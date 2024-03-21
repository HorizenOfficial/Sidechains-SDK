package io.horizen;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.horizen.account.secret.PrivateKeySecp256k1;
import io.horizen.account.secret.PrivateKeySecp256k1Creator;
import io.horizen.block.MainchainBlockReference;
import io.horizen.block.SidechainBlockBase;
import io.horizen.block.SidechainCreationVersions;
import io.horizen.block.SidechainsVersionsManager;
import io.horizen.companion.SidechainSecretsCompanion;
import io.horizen.cryptolibprovider.CircuitTypes;
import io.horizen.cryptolibprovider.CommonCircuit;
import io.horizen.cryptolibprovider.CryptoLibProvider;
import io.horizen.fork.ForkManager;
import io.horizen.params.MainNetParams;
import io.horizen.params.NetworkParams;
import io.horizen.params.RegTestParams;
import io.horizen.params.TestNetParams;
import io.horizen.proof.VrfProof;
import io.horizen.secret.*;
import io.horizen.tools.utils.Command;
import io.horizen.tools.utils.CommandProcessor;
import io.horizen.tools.utils.MessagePrinter;
import io.horizen.transaction.mainchain.SidechainCreation;
import io.horizen.transaction.mainchain.SidechainRelatedMainchainOutput;
import io.horizen.utils.*;
import io.horizen.vrf.VrfOutput;
import scala.Enumeration;
import scala.Option;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ScBootstrappingToolCommandProcessor extends CommandProcessor {

    // initialization of dlog key must be done only once by the rust cryptolib. Such data is stored in memory and is
    // used in both generateCertProofInfo and generateCswProofInfo cmds
    private static boolean dlogKeyInit = false;
    private static final int maxSeedLength = 1000;
    private static final int minSeedLength = 6;
    private final SidechainModel<?> scModel;

    private static boolean initDlogKey() {
        if (dlogKeyInit) {
            return true;
        }
        if (!CryptoLibProvider.commonCircuitFunctions().generateCoboundaryMarlinDLogKeys()) {
            return false;
        }
        dlogKeyInit = true;
        return true;
    }

    public ScBootstrappingToolCommandProcessor(MessagePrinter printer, SidechainModel<?> scModel) {
        super(printer);
        this.scModel = scModel;
    }

    @Override
    public void processCommand(String input) throws Exception {
        Command command = parseCommand(input);

        switch(command.name()) {
            case "help":
                printUsageMsg();
                break;
            case "generatekey":
                processGenerateKey(command.data());
                break;
            case "genesisinfo":
                processGenesisInfo(command.data());
                break;
            case "generateVrfKey":
                processGenerateVrfKey(command.data());
                break;
            case "generateAccountKey":
                processGenerateAccountKey(command.data());
                break;
            case "generateCertificateSignerKey":
                processGenerateCertificateSignerKey(command.data());
                break;
            case "generateCertProofInfo":
                processGenerateCertProofInfo(command.data());
                break;
            case "generateCertWithKeyRotationProofInfo":
                processGenerateCertWithKeyRotationProofInfo(command.data());
                break;
            case "generateCswProofInfo":
                processGenerateCswProofInfo(command.data());
                break;
            case "encodeString":
                processEncodeString(command.data());
                break;
            default:
                printUnsupportedCommandMsg(command.name());
        }
    }

    // Command structure is:
    // 1) <command name>
    // 1) <command name> <json argument>
    // 2) <command name> -f <path to file with json argument>
    @Override
    protected Command parseCommand(String input) throws IOException {
        String[] inputData = input.trim().split(" ", 2);
        if(inputData.length == 0)
            throw new IOException(String.format("Error: unrecognized input structure '%s'.%nSee 'help' for usage guideline.", input));

        ObjectMapper objectMapper = new ObjectMapper();
        // Check for command without arguments
        if(inputData.length == 1)
            return new Command(inputData[0], objectMapper.createObjectNode());

        String jsonData;
        String commandArguments = inputData[1].trim();
        // Check for file flag
        if(commandArguments.startsWith("-f ")) {
            // Remove '-f', possible around whitespaces and/or quotes
            String filePath = commandArguments.replaceAll("^-f\\s*\"*|\"$", "");
            // Try to open and read data from file
            try(
                    FileReader file = new FileReader(filePath);
                    BufferedReader reader = new BufferedReader(file)
            ) {
                jsonData = reader.readLine();
            } catch (FileNotFoundException e) {
                throw new IOException(String.format("Error: Input data file '%s' not found.%nSee 'help' for usage guideline.", filePath));
            }
        }
        else {
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
        printer.print("Usage:\n" +
                "\tFrom command line: <program name> <command name> [<json data>]\n" +
                "\tFor interactive mode: <command name> [<json data>]\n" +
                "\tRead command arguments from file: <command name> -f <path to file with json data>\n" +
                "Supported commands:\n" +
                "\thelp\n" +
                "\tgeneratekey <arguments>\n" +
                "\tgenerateVrfKey <arguments>\n" +
                "\tgenerateAccountKey <arguments>\n" +
                "\tgenerateCertificateSignerKey <arguments>\n" +
                "\tgenerateCertProofInfo <arguments>\n" +
                "\tgenerateCswProofInfo <arguments>\n" +
                "\tgenesisinfo <arguments>\n" +
                "\texit\n"
        );
    }

    private void printGenerateKeyUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tgeneratekey {\"seed\":\"my seed\"}" +
                " - seed can be any string from " + minSeedLength +" up to " + maxSeedLength + " characters long");
    }

    private void processGenerateKey(JsonNode json) {

        if(!json.has("seed") || !json.get("seed").isTextual()) {
            printGenerateKeyUsageMsg("seed is not specified or has invalid format.");
            return;
        }

        String seed = json.get("seed").asText();
        if (seed.length() < minSeedLength) {
            printGenerateKeyUsageMsg("seed is too short.");
            return;
        }

        if (seed.length() > maxSeedLength) {
            printGenerateKeyUsageMsg("seed is too long.");
            return;
        }

        PrivateKey25519 key = PrivateKey25519Creator.getInstance().generateSecret(seed.getBytes(StandardCharsets.UTF_8));

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("secret", BytesUtils.toHexString(secretsCompanion.toBytes(key)));
        resJson.put("publicKey", BytesUtils.toHexString(key.publicImage().bytes()));

        String res = resJson.toString();
        printer.print(res);
    }

    private void printGenerateVrfKeyUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tgenerateVrfKey {\"seed\":\"my seed\"}" +
                " - seed can be empty string or any string up to " + maxSeedLength + " characters long");
    }

    private  void processGenerateVrfKey(JsonNode json) {
        if(!json.has("seed") || !json.get("seed").isTextual()) {
            printGenerateVrfKeyUsageMsg("seed is not specified or has invalid format.");
            return;
        }

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        String seed = json.get("seed").asText();
        if (seed.length() > maxSeedLength) {
            printGenerateKeyUsageMsg("seed is too long.");
            return;
        }

        VrfSecretKey vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(seed.getBytes(StandardCharsets.UTF_8));

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("vrfSecret", BytesUtils.toHexString(secretsCompanion.toBytes(vrfSecretKey)));
        resJson.put("vrfPublicKey", BytesUtils.toHexString(vrfSecretKey.getPublicBytes()));

        String res = resJson.toString();
        printer.print(res);
    }

    private void printGenerateAccountKeyUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tgenerateAccountKey {\"seed\":\"my seed\"}" +
                " - seed can be any string from " + minSeedLength +" up to " + maxSeedLength + " characters long");
    }

    private void processGenerateAccountKey(JsonNode json) {
        if(!json.has("seed") || !json.get("seed").isTextual()) {
            printGenerateAccountKeyUsageMsg("seed is not specified or has invalid format.");
            return;
        }

        String seed = json.get("seed").asText();

        if (seed.length() < minSeedLength) {
            printGenerateKeyUsageMsg("seed is too short.");
            return;
        }

        if (seed.length() > maxSeedLength) {
            printGenerateKeyUsageMsg("seed is too long.");
            return;
        }

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());
        PrivateKeySecp256k1 secret = PrivateKeySecp256k1Creator.getInstance().generateSecret(seed.getBytes(StandardCharsets.UTF_8));

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("accountSecret", BytesUtils.toHexString(secretsCompanion.toBytes(secret)));
        resJson.put("accountProposition", BytesUtils.toHexString(secret.publicImage().pubKeyBytes()));

        String res = resJson.toString();
        printer.print(res);
    }

    private void printGenerateCertificateSignerKey(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tgenerateCertificateSignerKey {\"seed\":\"my seed\"}" +
                " - seed can be empty string or any string up to " + maxSeedLength + " characters long");
    }

    private void processGenerateCertificateSignerKey(JsonNode json) {
        if(!json.has("seed") || !json.get("seed").isTextual()) {
            printGenerateCertificateSignerKey("seed is not specified or has invalid format.");
            return;
        }

        String seed = json.get("seed").asText();
        if (seed.length() > maxSeedLength) {
            printGenerateKeyUsageMsg("seed is too long.");
            return;
        }

        SchnorrSecret secretKey = SchnorrKeyGenerator.getInstance().generateSecret(seed.getBytes(StandardCharsets.UTF_8));

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode resJson = mapper.createObjectNode();
        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        resJson.put("signerSecret", BytesUtils.toHexString(secretsCompanion.toBytes(secretKey)));
        resJson.put("signerPublicKey", BytesUtils.toHexString(secretKey.getPublicBytes()));

        String res = resJson.toString();
        printer.print(res);
    }

    private void printGenerateCertProofInfoUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tgenerateCertProofInfo {\"signersPublicKeys:\":\"[pk1, pk2, ...]\", \"threshold\":5, " +
                "\"provingKeyPath\": \"/tmp/sidechain/snark_proving_key\", " +
                "\"verificationKeyPath\": \"/tmp/sidechain/snark_verification_key\", "+
                "\"isCSWEnabled\": true}" +
                "\n\t - threshold parameter should be less or equal to keyCount." +
                "\n\t - isCSWEnabled parameter could be true or false."  );
    }

    private void processGenerateCertProofInfo(JsonNode json) {
        if (!json.has("signersPublicKeys") || !json.get("signersPublicKeys").isArray()) {
            printGenerateCertProofInfoUsageMsg("signersPublicKeys are missing or have unsupported format.");
            return;
        }

        List<String> publicKeys = new ArrayList<String>();

        Iterator<JsonNode> pksIterator = json.get("signersPublicKeys").elements();
        while (pksIterator.hasNext()) {
            JsonNode pkNode = pksIterator.next();

            if (!pkNode.isTextual()) {
                printGenerateCertProofInfoUsageMsg("wrong signersPublicKeys format");
                return;
            }

            publicKeys.add(pkNode.asText());
        }

        if (!json.has("threshold") || !json.get("threshold").isInt()) {
            printGenerateCertProofInfoUsageMsg("threshold is missing or it has unsupported format");
            return;
        }

        int threshold = json.get("threshold").asInt();

        if (threshold <= 0 || threshold > publicKeys.size()) {
            printGenerateCertProofInfoUsageMsg("threshold parameter should be greater than 0 and be less or equal to keyCount. Current value: " + threshold);
            return;
        }

        if (!json.has("provingKeyPath") || !json.get("provingKeyPath").isTextual()) {
            printGenerateCertProofInfoUsageMsg("wrong provingKeyPath value. Textual value expected.");
            return;
        }
        String provingKeyPath = json.get("provingKeyPath").asText();

        if (!json.has("verificationKeyPath") || !json.get("verificationKeyPath").isTextual()) {
            printGenerateCertProofInfoUsageMsg("wrong verificationKeyPath value. Textual value expected.");
            return;
        }
        String verificationKeyPath = json.get("verificationKeyPath").asText();

        if (!json.has("isCSWEnabled") || !json.get("isCSWEnabled").isBoolean()) {
            printGenerateCertProofInfoUsageMsg("wrong isCSWEnabled value. Boolean value expected.");
            return;
        }
        boolean isCSWEnabled = json.get("isCSWEnabled").asBoolean();

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        // Generate all keys only if verification key doesn't exist.
        // Note: we are interested only in verification key raw data.
        if(!Files.exists(Paths.get(verificationKeyPath))) {

            if (!initDlogKey()) {
                printer.print("Error occurred during dlog key generation.");
                return;
            }

            int numOfCustomFields = CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_NO_KEY_ROTATION;
            if (isCSWEnabled){
                numOfCustomFields = CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW;
            }
            if (!CryptoLibProvider.sigProofThresholdCircuitFunctions().generateCoboundaryMarlinSnarkKeys(publicKeys.size(), provingKeyPath, verificationKeyPath, numOfCustomFields)) {
                printer.print("Error occurred during snark keys generation.");
                return;
            }
        }
        // Read verification key from file
        String verificationKey = CryptoLibProvider.commonCircuitFunctions().getCoboundaryMarlinSnarkVerificationKeyHex(verificationKeyPath);
        if(verificationKey.isEmpty()) {
            printer.print("Verification key file is empty or the key is broken.");
            return;
        }

        List<byte[]> publicKeysBytes = publicKeys.stream().map(BytesUtils::fromHexString).collect(Collectors.toList());
        String genSysConstant = BytesUtils.toHexString(CryptoLibProvider.sigProofThresholdCircuitFunctions().generateSysDataConstant(publicKeysBytes, threshold));

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode resJson = mapper.createObjectNode();

        resJson.put("maxPks", publicKeys.size());
        resJson.put("threshold", threshold);
        resJson.put("genSysConstant", genSysConstant);
        resJson.put("verificationKey", verificationKey);

        ArrayNode keyArrayNode = resJson.putArray("schnorrPublicKeys");

        for (String publicKeyStr : publicKeys) {
            keyArrayNode.add(publicKeyStr);
        }

        String res = resJson.toString();
        printer.print(res);
    }

    private void printGenerateCertWithKeyRotationProofInfoUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tgenerateCertWithKeyRotationProofInfo {\"signersPublicKeys\": [signerPk1, signerPk2, ...], \"mastersPublicKeys\": [masterPk1, masterPk2, ...],\", \"threshold\":5, signersPublicKeys and mastersPublicKeys size should be equal," +
                "\"provingKeyPath\": \"/tmp/sidechain/snark_proving_key\", " +
                "\"verificationKeyPath\": \"/tmp/sidechain/snark_verification_key\"}"+
                "\n\t - threshold parameter should be less or equal to keyCount.");
    }
    private void processGenerateCertWithKeyRotationProofInfo(JsonNode json) throws Exception {
        if (!json.has("signersPublicKeys") || !json.get("signersPublicKeys").isArray()) {
            printGenerateCertWithKeyRotationProofInfoUsageMsg("signersPublicKeys are missing or have unsupported format.");
            return;
        }

        List<String> signersPublicKeys = new ArrayList<String>();

        Iterator<JsonNode> pksIterator = json.get("signersPublicKeys").elements();
        int index = 1;
        while (pksIterator.hasNext()) {
            JsonNode pkNode = pksIterator.next();

            if (!pkNode.isTextual()) {
                printGenerateCertWithKeyRotationProofInfoUsageMsg("wrong signersPublicKeys format");
                return;
            }

            String pk = pkNode.asText();

            int duplicateIndex = signersPublicKeys.indexOf(pk);
            if (duplicateIndex != -1) {
                printGenerateCertWithKeyRotationProofInfoUsageMsg(String.format("signersKeys contains duplicate values. SignersKey with index %d is identical to signersKey with index %d.", duplicateIndex + 1, index));
                return;
            }

            signersPublicKeys.add(pk);
            index++;
        }

        if (!json.has("mastersPublicKeys") || !json.get("mastersPublicKeys").isArray()) {
            printGenerateCertWithKeyRotationProofInfoUsageMsg("mastersPublicKeys are missing or have unsupported format.");
            return;
        }

        List<String> mastersPublicKeys = new ArrayList<>();

        Iterator<JsonNode> mastersPublicKeysIterator = json.get("mastersPublicKeys").elements();
        index = 1;
        while (mastersPublicKeysIterator.hasNext()) {
            JsonNode pkNode = mastersPublicKeysIterator.next();

            if (!pkNode.isTextual()) {
                printGenerateCertWithKeyRotationProofInfoUsageMsg("wrong mastersPublicKeys format");
                return;
            }

            String masterKey = pkNode.asText();

            int duplicateIndex = mastersPublicKeys.indexOf(masterKey);
            if (duplicateIndex != -1) {
                printGenerateCertWithKeyRotationProofInfoUsageMsg(String.format("masterKeys contains duplicate values. MasterKey with index %d is identical to masterKey with index %d.", duplicateIndex + 1, index));
                return;
            }

            mastersPublicKeys.add(masterKey);
            index++;
        }

        if (mastersPublicKeys.size() != signersPublicKeys.size()) {
            printGenerateCertWithKeyRotationProofInfoUsageMsg(String.format("the number of signer keys must be equal to the number of master keys."));
            return;
        }

        for(int i = 0; i < mastersPublicKeys.size(); i++) {
            int duplicateIndex = signersPublicKeys.indexOf(mastersPublicKeys.get(i));
            if(duplicateIndex != -1) {
                printGenerateCertWithKeyRotationProofInfoUsageMsg(String.format("duplicated keys are found. SignersKey with index %d equals to mastersKey with index %d", duplicateIndex, i));
                return;
            }
        }

        if (!json.has("threshold") || !json.get("threshold").isInt()) {
            printGenerateCertWithKeyRotationProofInfoUsageMsg("threshold is missing or it has unsupported format");
            return;
        }

        int threshold = json.get("threshold").asInt();

        if (threshold <= 0 || threshold > signersPublicKeys.size()) {
            printGenerateCertWithKeyRotationProofInfoUsageMsg("threshold parameter should be greater than 0 and be less than or equal to keyCount. Current value: " + threshold);
            return;
        }

        if (!json.has("provingKeyPath") || !json.get("provingKeyPath").isTextual()) {
            printGenerateCertWithKeyRotationProofInfoUsageMsg("wrong provingKeyPath value. Textual value expected.");
            return;
        }
        String provingKeyPath = json.get("provingKeyPath").asText();

        if (!json.has("verificationKeyPath") || !json.get("verificationKeyPath").isTextual()) {
            printGenerateCertWithKeyRotationProofInfoUsageMsg("wrong verificationKeyPath value. Textual value expected.");
            return;
        }
        String verificationKeyPath = json.get("verificationKeyPath").asText();

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        // Generate all keys only if verification key doesn't exist.
        // Note: we are interested only in verification key raw data.
        if(!Files.exists(Paths.get(verificationKeyPath))) {

            if (!initDlogKey()) {
                printer.print("Error occurred during dlog key generation.");
                return;
            }

            if (!CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation().generateCoboundaryMarlinSnarkKeys(signersPublicKeys.size(), provingKeyPath, verificationKeyPath)) {
                printer.print("Error occurred during snark keys generation.");
                return;
            }
        }
        // Read verification key from file
        String verificationKey = CryptoLibProvider.commonCircuitFunctions().getCoboundaryMarlinSnarkVerificationKeyHex(verificationKeyPath);
        if(verificationKey.isEmpty()) {
            printer.print("Verification key file is empty or the key is broken.");
            return;
        }

        List<byte[]> signersPublicKeysBytes = signersPublicKeys.stream().map(BytesUtils::fromHexString).collect(Collectors.toList());
        List<byte[]> mastersPublicKeysBytes = mastersPublicKeys.stream().map(BytesUtils::fromHexString).collect(Collectors.toList());
        String genSysConstant = BytesUtils.toHexString(CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation().generateSysDataConstant(signersPublicKeysBytes, mastersPublicKeysBytes, threshold));

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode resJson = mapper.createObjectNode();

        resJson.put("maxPks", signersPublicKeys.size());
        resJson.put("threshold", threshold);
        resJson.put("genSysConstant", genSysConstant);
        resJson.put("verificationKey", verificationKey);

        ArrayNode signingKeyArrayNode = resJson.putArray("signersPublicKeys");

        for (String publicKeyStr : signersPublicKeys) {
            signingKeyArrayNode.add(publicKeyStr);
        }

        ArrayNode masterKeyArrayNode = resJson.putArray("mastersPublicKeys");
        for (String publicKeyStr : mastersPublicKeys) {
            masterKeyArrayNode.add(publicKeyStr);
        }

        String res = resJson.toString();
        printer.print(res);
    }

    private void printGenerateCswProofInfoUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tgenerateCswProofInfo {\"withdrawalEpochLen\":100, " +
                "\"provingKeyPath\": \"/tmp/sidechain/csw_proving_key\", " +
                "\"verificationKeyPath\": \"/tmp/sidechain/csw_verification_key\" }");
    }

    private void printEncodeStringUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tencodeString {\"string\":\"string_to_encode\"}");
    }

    private void processGenerateCswProofInfo(JsonNode json) {

        if (!json.has("withdrawalEpochLen") || !json.get("withdrawalEpochLen").isInt()) {
            printGenerateCswProofInfoUsageMsg("wrong withdrawalEpochLen");
            return;
        }

        int withdrawalEpochLen = json.get("withdrawalEpochLen").asInt();

        if (withdrawalEpochLen <= 0) {
            printGenerateCswProofInfoUsageMsg("wrong withdrawalEpochLen: " + withdrawalEpochLen);
            return;
        }

        if (!json.has("provingKeyPath") || !json.get("provingKeyPath").isTextual()) {
            printGenerateCswProofInfoUsageMsg("wrong provingKeyPath value. Textual value expected.");
            return;
        }
        String provingKeyPath = json.get("provingKeyPath").asText();

        if (!json.has("verificationKeyPath") || !json.get("verificationKeyPath").isTextual()) {
            printGenerateCswProofInfoUsageMsg("wrong verificationKeyPath value. Textual value expected.");
            return;
        }
        String verificationKeyPath = json.get("verificationKeyPath").asText();

        // Generate all keys only if verification key doesn't exist.
        // Note: we are interested only in verification key raw data.
        if(!Files.exists(Paths.get(verificationKeyPath))) {
            if (!initDlogKey()) {
                printer.print("Error occurred during dlog key generation.");
                return;
            }

            if (!CryptoLibProvider.cswCircuitFunctions().generateCoboundaryMarlinSnarkKeys(withdrawalEpochLen, provingKeyPath, verificationKeyPath)) {
                printer.print("Error occurred during snark keys generation.");
                return;
            }
        }
        // Read verification key from file
        String verificationKey = CryptoLibProvider.commonCircuitFunctions().getCoboundaryMarlinSnarkVerificationKeyHex(verificationKeyPath);
        if(verificationKey.isEmpty()) {
            printer.print("Verification key file is empty or the key is broken.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode resJson = mapper.createObjectNode();

        resJson.put("withdrawalEpochLen", withdrawalEpochLen);
        resJson.put("verificationKey", verificationKey);

        String res = resJson.toString();
        printer.print(res);
    }

    private void processEncodeString(JsonNode json ) {
        if (!json.has("string") || !json.get("string").isTextual()) {
            printEncodeStringUsageMsg("wrong string");
            return;
        }
        String toEncode = json.get("string").asText();
        String encoded = BCrypt.with(BCrypt.Version.VERSION_2Y).hashToString(8, toEncode.toCharArray());

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("encodedString", encoded);

        String res = resJson.toString();
        printer.print(res);
    }


    private void printGenesisInfoUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                      "\tgenesisinfo {\n" +
                      "\t\t\"secret\": <secret hex>, - private key to sign the sc genesis block\n" +
                      "\t\t\"vrfSecret\": <vrf secret hex>, secret vrf key\n" +
                      "\t\t\"info\": <sc genesis info hex> - hex data retrieved from MC RPC call 'getscgenesisinfo'\n" +
                      "\t\t\"virtualWithdrawalEpochLength\": optional field used for non-ceasing sidechain to specify the cert generation frequency.\n" +
                      "\t\t\"updateconfig\": boolean - Optional. Default false. If true, put the results in a copy of source config.\n" +
                      "\t\t\"sourceconfig\": <path to in config file> - expected if 'updateconfig' = true.\n" +
                      "\t\t\"resultconfig\": <path to out config file> - expected if 'updateconfig' = true.\n" +
                      "\t\t\"model\": String - Optional, default = 'utxo', 'account' model.\n" +
                "\t}"
        );
        printer.print("Examples:\n" +
                      "\tgenesisinfo {\"secret\":\"78fa...e818\", \"info\":\"0001....ad11\"}\n\n" +
                      "\tgenesisinfo {\"secret\":\"78fa...e818\", \"info\":\"0001....ad11\", \"model\":\"account\"}\n\n" +
                      "\tgenesisinfo {\"secret\":\"78fa...e818\", \"info\":\"0001....ad11\", \n" +
                      "\t\"updateconfig\": true, \"sourceconfig\":\"./template.conf\", \"resultconfig\":\"./result.conf\"}");
    }

    private void processGenesisInfo(JsonNode json) {
        if (!json.has("info") || !json.get("info").isTextual()
                || !json.has("vrfSecret") || !json.get("vrfSecret").isTextual()
                || !json.has("secret") || !json.get("secret").isTextual()) {
            printGenesisInfoUsageMsg("wrong arguments syntax.");
            return;
        }

        String model;
        if (json.has("model"))
        {
            model = json.get("model").asText();
            if ( !model.equals("account") &&
                    !model.equals("utxo"))
            {
                printGenesisInfoUsageMsg("Optional 'model' string field expected to be 'utxo' or 'account'.");
                return;
            }
        } else {
            model = "utxo";
        }

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        String infoHex = json.get("info").asText();
        byte[] infoBytes;
        try {
            infoBytes = BytesUtils.fromHexString(infoHex);
        } catch (IllegalArgumentException e) {
            printGenesisInfoUsageMsg("'info' expected to be a hex string.");
            return;
        }

        String secretHex = json.get("secret").asText();

        byte[] secretBytes;
        try {
            secretBytes = BytesUtils.fromHexString(secretHex);
        } catch (IllegalArgumentException e) {
            printGenesisInfoUsageMsg("'secret' expected to be a hex string.");
            return;
        }

        PrivateKey25519 key;
        try {
            key = (PrivateKey25519) secretsCompanion.parseBytes(secretBytes);
        } catch (Exception e) {
            printGenesisInfoUsageMsg("'secret' value is broken. Can't deserialize the key.");
            return;
        }

        String vrfSecretHex = json.get("vrfSecret").asText();

        byte[] vrfSecretBytes;
        try {
            vrfSecretBytes = BytesUtils.fromHexString(vrfSecretHex);
        } catch (IllegalArgumentException e) {
            printGenesisInfoUsageMsg("'secret' expected to be a hex string.");
            return;
        }

        VrfSecretKey vrfSecretKey;
        try {
            vrfSecretKey = (VrfSecretKey) secretsCompanion.parseBytes(vrfSecretBytes);
        } catch (Exception e) {
            printGenesisInfoUsageMsg("'vrfSecret' value is broken. Can't deserialize the key.");
            return;
        }

        // virtualWithdrawalEpochLength is an optional field that
        int virtualWithdrawalEpochLength = 0;
        if (json.has("virtualWithdrawalEpochLength")) {
            if(!json.get("virtualWithdrawalEpochLength").isInt()) {
                printGenesisInfoUsageMsg("'virtualWithdrawalEpochLength' should be integer.");
                return;
            }
            virtualWithdrawalEpochLength = json.get("virtualWithdrawalEpochLength").asInt();

            if (virtualWithdrawalEpochLength < 0) {
                printGenesisInfoUsageMsg("'virtualWithdrawalEpochLength' can't be negative.");
                return;
            }
        }

        boolean shouldUpdateConfig = json.has("updateconfig") && json.get("updateconfig").asBoolean();
        if(shouldUpdateConfig &&
                (!json.has("sourceconfig") || !json.get("sourceconfig").isTextual() ||
                        !json.has("resultconfig") || !json.get("resultconfig").isTextual())) {
            printGenesisInfoUsageMsg("'updateconfig' is specified but path to configs doesn't not.");
            return;
        }

        // Parsing the info: scid, powdata vector, mc block height, mc block hex, mc initial BlockSCTxCommTreeCumulativeHash
        int offset = 0;
        try {
            byte network = infoBytes[offset];
            offset += 1;

            // Keep scId in original LE
            byte[] scId = Arrays.copyOfRange(infoBytes, offset, offset + 32);
            offset += 32;

            CompactSize powDataLength = BytesUtils.getCompactSize(infoBytes, offset);
            offset += powDataLength.size();

            String powData = BytesUtils.toHexString(Arrays.copyOfRange(infoBytes, offset, offset + ((int)powDataLength.value() * 8)));
            offset += powDataLength.value() * 8;

            int mcBlockHeight = BytesUtils.getReversedInt(infoBytes, offset);
            offset += 4;

            CompactSize initialCumulativeCommTreeHashLength = BytesUtils.getCompactSize(infoBytes, offset);
            offset += initialCumulativeCommTreeHashLength.size();

            // Note: we keep this value in Little endian as expected by sc-cryptolib
            byte[] initialCumulativeCommTreeHash = Arrays.copyOfRange(infoBytes, offset, offset + (int)initialCumulativeCommTreeHashLength.value());
            offset += initialCumulativeCommTreeHashLength.value();

            byte[] rest = Arrays.copyOfRange(infoBytes, offset, infoBytes.length);
            Integer mcBlockLength = (Integer) MainchainBlockReference.parseMainchainBlockBytes(rest).get()._4();
            byte[] mcBlockBytes = Arrays.copyOfRange(infoBytes, offset, offset + mcBlockLength);
            offset += mcBlockLength;

            // For the MC nodes after v3.0.3 genesis info also contains sidechain creation versions for the certificates
            // in the given MC block to allow us to reconstruct the MainchainBlockReference in a proper way.
            SidechainsVersionsManager versionsManager = null;
            if(offset < infoBytes.length) {
                Map<ByteArrayWrapper, Enumeration.Value> scVersions = new HashMap<>();
                CompactSize scSidechainVersionsLength = BytesUtils.getCompactSize(infoBytes, offset);
                offset += scSidechainVersionsLength.size();
                for (int i = 0; i < scSidechainVersionsLength.value(); i++) {
                    byte[] sidechainId = Arrays.copyOfRange(infoBytes, offset, offset + 32);
                    offset += 32;
                    byte version = infoBytes[offset];
                    offset += 1;
                    scVersions.put(new ByteArrayWrapper(sidechainId), SidechainCreationVersions.getVersion(version));
                }
                versionsManager = new NewSidechainsVersionsManager(scVersions);
            } else {
                versionsManager = new OldSidechainsVersionsManager();
            }

            String mcNetworkName = getNetworkName(network);
            NetworkParams params = getNetworkParams(network, scId, false);
            // Uncomment if you want to save mc block hex for some reason
            /* try (PrintStream out = new PrintStream(new FileOutputStream("c:/mchex.txt"))) {
                out.print(BytesUtils.toHexString(Arrays.copyOfRange(infoBytes, offset, infoBytes.length)));
            }*/

            MainchainBlockReference mcRef = MainchainBlockReference.create(mcBlockBytes, params, versionsManager).get();

            //Find Sidechain creation information
            SidechainCreation sidechainCreation = null;
            if (mcRef.data().sidechainRelatedAggregatedTransaction().isEmpty())
                throw new IllegalArgumentException("Sidechain related data is not found in genesisinfo mc block.");

            for (SidechainRelatedMainchainOutput output : mcRef.data().sidechainRelatedAggregatedTransaction().get().mc2scTransactionsOutputs()) {
                if (output instanceof SidechainCreation) {
                    sidechainCreation =  (SidechainCreation) output;
                }
            }

            boolean isNewCircuit = sidechainCreation.getScCrOutput().fieldElementCertificateFieldConfigs().length()
                    == CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION;
            params = getNetworkParams(network, scId, isNewCircuit);

            if (sidechainCreation == null)
                throw new IllegalArgumentException("Sidechain creation transaction is not found in genesisinfo mc block.");

            byte[] vrfMessage =  "!SomeVrfMessage1!SomeVrfMessage2".getBytes(StandardCharsets.UTF_8);
            VrfProof vrfProof = vrfSecretKey.prove(vrfMessage).getKey();
            VrfOutput vrfOutput = vrfProof.proofToVrfOutput(vrfSecretKey.publicImage(), vrfMessage).get();
            MerklePath mp = new MerklePath(new ArrayList<>());

            // initialize ForkManager because genesis parameters might depend on forks activating at epoch 0
            ForkManager.reset();
            ForkManager.init(scModel.getForkConfigurator(), mcNetworkName);

            int withdrawalEpochLength;
            String sidechainBlockHex;

            // are we building a utxo or account model based block?
            SidechainBlockBase<?, ?> blockBase = scModel.buildScGenesisBlock(
                    mcRef, sidechainCreation, json, key, vrfProof, vrfOutput, mp, params
            );
            try {
                SidechainCreation creationOutput = (SidechainCreation) blockBase.mainchainBlockReferencesData().head().sidechainRelatedAggregatedTransaction().get().mc2scTransactionsOutputs().get(0);
                withdrawalEpochLength = creationOutput.withdrawalEpochLength();
            } catch (Exception e) {
                printGenesisInfoUsageMsg("'info' data is corrupted: MainchainBlock expected to contain a valid Transaction with a Sidechain Creation output.");
                return;
            }
            sidechainBlockHex = BytesUtils.toHexString(blockBase.bytes());

            boolean isNonCeasing = (withdrawalEpochLength == 0);

            if (isNonCeasing && virtualWithdrawalEpochLength == 0) {
                printGenesisInfoUsageMsg("For non-ceasing sidechains virtualWithdrawalEpochLength must be specified.");
                return;
            }

            if (!isNonCeasing && virtualWithdrawalEpochLength != 0) {
                printGenesisInfoUsageMsg("For ceasing sidechains virtualWithdrawalEpochLength must not be specified.");
                return;
            }

            if (isNonCeasing && virtualWithdrawalEpochLength < params.minVirtualWithdrawalEpochLength()) {
                printGenesisInfoUsageMsg(String.format("Virtual withdrawal epoch length is too short. It should be at least %d for %s network.", params.minVirtualWithdrawalEpochLength(), mcNetworkName));
                return;
            }

            ObjectNode resJson = new ObjectMapper().createObjectNode();
            resJson.put("scId", BytesUtils.toHexString(BytesUtils.reverseBytes(scId))); // scId output expected to be in BE
            resJson.put("scGenesisBlockHex", sidechainBlockHex);
            resJson.put("powData", powData);
            resJson.put("mcBlockHeight", mcBlockHeight);
            resJson.put("mcNetwork", mcNetworkName);
            resJson.put("isNonCeasing", isNonCeasing);
            resJson.put("withdrawalEpochLength", isNonCeasing ? virtualWithdrawalEpochLength : withdrawalEpochLength);
            resJson.put("initialCumulativeCommTreeHash", BytesUtils.toHexString(initialCumulativeCommTreeHash));
            String res = resJson.toString();
            printer.print(res);

            if(shouldUpdateConfig)
                updateTemplateFile(
                        json.get("sourceconfig").asText(),
                        json.get("resultconfig").asText(),
                        mcBlockHeight,
                        powData,
                        BytesUtils.toHexString(scId),
                        sidechainBlockHex,
                        mcNetworkName,
                        withdrawalEpochLength,
                        BytesUtils.toHexString(initialCumulativeCommTreeHash)
                );
        } catch (Exception e) {
            printer.print(String.format("Error: 'info' data is corrupted: %s", e.getMessage()));
        }
    }

    private String getNetworkName(byte network) {
        switch(network) {
            case 0:
                return "mainnet";
            case 1:
                return "testnet";
            case 2:
                return "regtest";
        }
        return "";
    }

    private NetworkParams getNetworkParams(byte network, byte[] scId, boolean isNewCircuit) {
        Enumeration.Value circuitType = isNewCircuit
                ? CircuitTypes.NaiveThresholdSignatureCircuitWithKeyRotation()
                : CircuitTypes.NaiveThresholdSignatureCircuit();

        switch(network) {
            case 0: // mainnet
                return new MainNetParams(scId, null, null, null, null, 1, 0,100, null, null, circuitType,0, null, null, null, null, null, null, null, false, null, null, 11111111,true, false, true, 0, 840000,  false, Option.empty());
            case 1: // testnet
                return new TestNetParams(scId, null, null, null, null, 1, 0, 100, null, null, circuitType, 0, null, null, null, null, null, null, null, false, null, null, 11111111,true, false, true, 0, 840000, false, Option.empty());
            case 2: // regtest
                return new RegTestParams(scId, null, null, null, null, 1, 0, 100, null, null, circuitType, 0, null, null, null, null, null, null, null, false, null, null, 11111111,true, false, true, 0, 2000, false, 0, Option.empty());

            default:
                throw new IllegalStateException("Unexpected network type: " + network);
        }
    }

    private void updateTemplateFile(
            String pathToSourceConfig,
            String pathToResultConf,
            int mcBlockHeight,
            String powData,
            String scId,
            String scBlockHex,
            String mcNetworkName,
            int withdrawalEpochLength,
            String initialCumulativeCommTreeHashHex) {
        try {
            String templateConf = new String(Files.readAllBytes(Paths.get(pathToSourceConfig)), StandardCharsets.UTF_8);


            String conf = templateConf +
                    "\nsparkz {\n" +
                    "\tgenesis {\n" +
                    "\t\tscGenesisBlockHex = \"" + scBlockHex + "\"\n" +
                    "\t\tscId = \"" + scId + "\"\n" +
                    "\t\tpowData = \"" + powData + "\"\n" +
                    "\t\tmcBlockHeight = " + mcBlockHeight + "\n" +
                    "\t\tmcNetwork = " + mcNetworkName + "\n" +
                    "\t\twithdrawalEpochLength = " + withdrawalEpochLength + "\n" +
                    "\t\tinitialCumulativeCommTreeHash = \"" + initialCumulativeCommTreeHashHex + "\"\n" +
                    "\t}\n" +
                    "}\n";

            Files.write(Paths.get(pathToResultConf), conf.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            printer.print("Error: unable to open config file.");
        }
    }
}
