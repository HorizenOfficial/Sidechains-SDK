package com.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.block.MainchainBlockReference;
import com.horizen.block.MainchainHeader;
import com.horizen.block.Ommer;
import com.horizen.block.SidechainBlock;
import com.horizen.box.ForgerBox;
import com.horizen.box.NoncedBox;
import com.horizen.companion.SidechainBoxesDataCompanion;
import com.horizen.companion.SidechainProofsCompanion;
import com.horizen.companion.SidechainSecretsCompanion;
import com.horizen.companion.SidechainTransactionsCompanion;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.params.MainNetParams;
import com.horizen.params.NetworkParams;
import com.horizen.params.RegTestParams;
import com.horizen.params.TestNetParams;
import com.horizen.proof.SchnorrProof;
import com.horizen.proof.VrfProof;
import com.horizen.proposition.Proposition;
import com.horizen.secret.*;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.transaction.mainchain.SidechainCreation;
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.MerklePath;
import com.horizen.utils.VarInt;
import scorex.core.transaction.state.SecretCompanion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class CommandProcessor {
    private MessagePrinter printer;

    public CommandProcessor(MessagePrinter printer) {
        this.printer = printer;
    }

    public void processCommand(String input) throws IOException {
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
            case "generateSchnorrKeys":
                processGenerateSchnorrKeys(command.data());
                break;
            default:
                printUnsupportedCommandMsg(command.name());
        }
    }

    // Command structure is: command_name [json_argument]
    private Command parseCommand(String input) throws IOException {
        String inputData[] = input.trim().split(" ", 2);
        if(inputData.length == 0)
            throw new IOException(String.format("Error: unrecognized input structure '%s'.\nSee 'help' for usage guideline.", input));

        ObjectMapper objectMapper = new ObjectMapper();
        if(inputData.length != 2)
            return new Command(inputData[0], objectMapper.createObjectNode());

        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(inputData[1]);
        } catch (Exception e) {
            throw new IOException(String.format("Error: Invalid input data format '%s'. Json expected.\nSee 'help' for usage guideline.", inputData[1]));
        }

        return new Command(inputData[0], jsonNode);
    }

    private void printUsageMsg() {
        printer.print("Usage:\n" +
                      "\tFrom command line: <program name> <command name> [<json data>]\n" +
                      "\tFor interactive mode: <command name> [<json data>]\n" +
                      "Supported commands:\n" +
                      "\thelp\n" +
                      "\tgeneratekey <arguments>\n" +
                      "\tgenerateVrfKey <arguments>\n" +
                      "\tgenerateSchnorrKeys <arguments>\n" +
                      "\tgenesisinfo <arguments>\n" +
                      "\texit\n"
        );
    }

    private void printUnsupportedCommandMsg(String command) {
        printer.print(String.format("Error: unsupported command '%s'.\nSee 'help' for usage guideline.", command));
    }

    private void printGenerateKeyUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                      "\tgeneratekey {\"seed\":\"my seed\"}");
    }

    private void processGenerateKey(JsonNode json) {

        if(!json.has("seed") || !json.get("seed").isTextual()) {
            printGenerateKeyUsageMsg("seed is not specified or has invalid format.");
            return;
        }
        PrivateKey25519 key = PrivateKey25519Creator.getInstance().generateSecret(json.get("seed").asText().getBytes());

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
                      "\tgenerateVrfKey {\"seed\":\"my seed\"}");
    }

    private  void processGenerateVrfKey(JsonNode json) {

        if(!json.has("seed") || !json.get("seed").isTextual()) {
            printGenerateVrfKeyUsageMsg("seed is not specified or has invalid format.");
            return;
        }

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        VrfSecretKey vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(json.get("seed").asText().getBytes());

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("vrfSecret", BytesUtils.toHexString(secretsCompanion.toBytes(vrfSecretKey)));
        resJson.put("vrfPublicKey", BytesUtils.toHexString(vrfSecretKey.getPublicBytes()));

        String res = resJson.toString();
        printer.print(res);
    }

    private void printGenerateSchnorrKeysUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                      "\tgenerateSchnorrKeys {\"seed\":\"my seed\", \"keyCount\":10, \"threshold\":5}" +
                      "\tthreshold parameter should be less or equal to keyCount.");
    }

    private void processGenerateSchnorrKeys(JsonNode json) {

        if(!json.has("seed") || !json.get("seed").isTextual()) {
            printGenerateSchnorrKeysUsageMsg("seed is not specified or has invalid format.");
            return;
        }

        if (!json.has("keyCount") || !json.get("keyCount").isInt()) {
            printGenerateSchnorrKeysUsageMsg("wrong key count");
            return;
        }

        int keyCount = json.get("keyCount").asInt();

        if (keyCount <= 0) {
            printGenerateSchnorrKeysUsageMsg("wrong key count - " + keyCount);
            return;
        }

        if (!json.has("threshold") || !json.get("threshold").isInt()) {
            printGenerateSchnorrKeysUsageMsg("wrong threshold");
            return;
        }

        int threshold = json.get("threshold").asInt();

        if (threshold <= 0 || threshold > keyCount) {
            printGenerateSchnorrKeysUsageMsg("wrong threshold - " + threshold);
            return;
        }

        //@TODO remove hardcoded value
        if (threshold != 5 || keyCount != 7) {
            printGenerateSchnorrKeysUsageMsg("Currently supported only 7 keys and threshold 5");
            return;
        }

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        List<SchnorrSecret> secretKeys = new ArrayList<>();

        for (int i = 0; i < keyCount; i++ ) {
            secretKeys.add(SchnorrKeyGenerator.getInstance()
                    .generateSecret(Bytes.concat(json.get("seed").asText().getBytes(), Ints.toByteArray(i))));
        }

        List<byte[]> publicKeysBytes = secretKeys.stream().map(SchnorrSecret::getPublicBytes).collect(Collectors.toList());
        byte[] genSysConstant = CryptoLibProvider.sigProofThresholdCircuitFunctions().generateSysDataConstant(publicKeysBytes, threshold);
        //byte[] verificationKey =

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode resJson = mapper.createObjectNode();

        resJson.put("threshold", threshold);

        ArrayNode keyArrayNode = resJson.putArray("schnorrKeys");

        for (SchnorrSecret secretKey : secretKeys) {
            ObjectNode keyNode = mapper.createObjectNode();
            keyNode.put("schnorrSecret", BytesUtils.toHexString(secretsCompanion.toBytes(secretKey)));
            keyNode.put("schnorrPublicKey", BytesUtils.toHexString(secretKey.getPublicBytes()));
            keyArrayNode.add(keyNode);
        }

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
                      "\t\t\"updateconfig\": boolean - Optional. Default false. If true, put the results in a copy of source config.\n" +
                      "\t\t\"sourceconfig\": <path to in config file> - expected if 'updateconfig' = true.\n" +
                      "\t\t\"resultconfig\": <path to out config file> - expected if 'updateconfig' = true.\n" +
                      "\t}"
        );
        printer.print("Examples:\n" +
                      "\tgenesisinfo {\"secret\":\"78fa...e818\", \"info\":\"0001....ad11\"}\n\n" +
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

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        String infoHex = json.get("info").asText();
        byte infoBytes[];
        try {
            infoBytes = BytesUtils.fromHexString(infoHex);
        } catch (IllegalArgumentException e) {
            printGenesisInfoUsageMsg("'info' expected to be a hex string.");
            return;
        }

        String secretHex = json.get("secret").asText();
        if(secretHex.length() != 130) {// size of hex representation of Private25519Key
            printGenesisInfoUsageMsg("'secret' value size is wrong.");
            return;
        }

        byte[] secretBytes;
        try {
            secretBytes = BytesUtils.fromHexString(secretHex);
        } catch (IllegalArgumentException e) {
            printGenesisInfoUsageMsg("'secret' expected to be a hex string.");
            return;
        }

        PrivateKey25519 key = (PrivateKey25519) secretsCompanion.parseBytes(secretBytes);

        String vrfSecretHex = json.get("vrfSecret").asText();
        if(vrfSecretHex.length() != 588) {// size of hex representation of VrfSecretKey
            printGenesisInfoUsageMsg("'vrfSecret' value size is wrong.");
            return;
        }

        byte[] vrfSecretBytes;
        try {
            vrfSecretBytes = BytesUtils.fromHexString(vrfSecretHex);
        } catch (IllegalArgumentException e) {
            printGenesisInfoUsageMsg("'secret' expected to be a hex string.");
            return;
        }

        VrfSecretKey vrfSecretKey = (VrfSecretKey) secretsCompanion.parseBytes(vrfSecretBytes);

        boolean shouldUpdateConfig = json.has("updateconfig") && json.get("updateconfig").asBoolean();
        if(shouldUpdateConfig &&
                        (!json.has("sourceconfig") || !json.get("sourceconfig").isTextual() ||
                        !json.has("resultconfig") || !json.get("resultconfig").isTextual())) {
            printGenesisInfoUsageMsg("'updateconfig' is specified but path to configs doesn't not.");
            return;
        }

        // Parsing the info: scid, powdata vector, mc block height, mc block hex
        int offset = 0;
        try {
            byte network = infoBytes[offset];
            offset += 1;

            byte scId[] = BytesUtils.reverseBytes(Arrays.copyOfRange(infoBytes, offset, offset + 32));
            offset += 32;

            VarInt powDataLength = BytesUtils.getVarInt(infoBytes, offset);
            offset += powDataLength.size();

            String powData = BytesUtils.toHexString(Arrays.copyOfRange(infoBytes, offset, offset + ((int)powDataLength.value() * 8)));
            offset += powDataLength.value() * 8;

            int mcBlockHeight = BytesUtils.getReversedInt(infoBytes, offset);
            offset += 4;

            String mcNetworkName = getNetworkName(network);
            NetworkParams params = getNetworkParams(network, scId);
            MainchainBlockReference mcRef = MainchainBlockReference.create(Arrays.copyOfRange(infoBytes, offset, infoBytes.length), params).get();


            SidechainBoxesDataCompanion sidechainBoxesDataCompanion = new SidechainBoxesDataCompanion(new HashMap<>());
            SidechainProofsCompanion sidechainProofsCompanion = new SidechainProofsCompanion(new HashMap<>());
            SidechainTransactionsCompanion sidechainTransactionsCompanion = new SidechainTransactionsCompanion(new HashMap<>(), sidechainBoxesDataCompanion, sidechainProofsCompanion);

            //Find Sidechain creation information
            SidechainCreation sidechainCreation = null;
            for (SidechainRelatedMainchainOutput output : mcRef.data().sidechainRelatedAggregatedTransaction().get().mc2scTransactionsOutputs()) {
                if (output instanceof SidechainCreation) {
                    sidechainCreation =  (SidechainCreation) output;
                }
            }

            if (sidechainCreation == null)
                throw new IllegalArgumentException("Sidechain creation transaction is not found in genesisinfo.");

            ForgerBox forgerBox = sidechainCreation.getBox();
            byte[] vrfMessage =  "!SomeVrfMessage1!SomeVrfMessage2".getBytes();
            VrfProof vrfProof  = vrfSecretKey.prove(vrfMessage).getKey();
            MerklePath mp = new MerklePath(new ArrayList<>());
            // Set genesis block timestamp to not to have block in future exception during STF tests.
            // TODO: timestamp should be a hidden optional parameter during SC bootstrapping and must be used by STF
            Long timestamp = System.currentTimeMillis() / 1000 - (params.consensusSlotsInEpoch() / 2 * params.consensusSecondsInSlot());

            SidechainBlock sidechainBlock = SidechainBlock.create(
                    params.sidechainGenesisBlockParentId(),
                    timestamp,
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(mcRef.data())).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<SidechainTransaction<Proposition, NoncedBox<Proposition>>>()).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(mcRef.header())).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<Ommer>()).asScala().toSeq(),
                    key,
                    forgerBox,
                    vrfProof,
                    mp,
                    sidechainTransactionsCompanion,
                    params,
                    scala.Option.empty()
            ).get();

            int withdrawalEpochLength;
            try {
                SidechainCreation creationOutput = (SidechainCreation) sidechainBlock.mainchainBlockReferencesData().head().sidechainRelatedAggregatedTransaction().get().mc2scTransactionsOutputs().get(0);
                withdrawalEpochLength = creationOutput.withdrawalEpochLength();
            }
            catch (Exception e) {
                printGenesisInfoUsageMsg("'info' data is corrupted: MainchainBlock expected to contain a valid Transaction with a Sidechain Creation output.");
                return;
            }

            String sidechainBlockHex = BytesUtils.toHexString(sidechainBlock.bytes());


            ObjectNode resJson = new ObjectMapper().createObjectNode();
            resJson.put("scId", BytesUtils.toHexString(scId));
            resJson.put("scGenesisBlockHex", sidechainBlockHex);
            resJson.put("powData", powData);
            resJson.put("mcBlockHeight", mcBlockHeight);
            resJson.put("mcNetwork", mcNetworkName);
            resJson.put("withdrawalEpochLength", withdrawalEpochLength);
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
                        withdrawalEpochLength
                );
        } catch (Exception e) {
            printer.print("Error: 'info' data is corrupted.");
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

    private NetworkParams getNetworkParams(byte network, byte[] scId) {
        switch(network) {
            case 0: // mainnet
                return new MainNetParams(scId, null, null, null, null, 1, 0,100, 120, 720, null, 0, null, null);
            case 1: // testnet
                return new TestNetParams(scId, null, null, null, null, 1, 0, 100, 120, 720, null, 0, null, null);
            case 2: // regtest
                return new RegTestParams(scId, null, null, null, null, 1, 0, 100, 120, 720, null, 0, null, null);
        }
        return null;

    }

    private void updateTemplateFile(
            String pathToSourceConfig,
            String pathToResultConf,
            int mcBlockHeight,
            String powData,
            String scId,
            String scBlockHex,
            String mcNetworkName,
            int withdrawalEpochLength) {
        try {
            String templateConf = new String(Files.readAllBytes(Paths.get(pathToSourceConfig)), StandardCharsets.UTF_8);


            String conf = templateConf +
                          "\nscorex {\n" +
                          "\tgenesis {\n" +
                          "\t\tscGenesisBlockHex = \"" + scBlockHex + "\"\n" +
                          "\t\tscId = \"" + scId + "\"\n" +
                          "\t\tpowData = \"" + powData + "\"\n" +
                          "\t\tmcBlockHeight = " + mcBlockHeight + "\n" +
                          "\t\tmcNetwork = " + mcNetworkName + "\n" +
                          "\t\twithdrawalEpochLength = " + withdrawalEpochLength + "\n" +
                          "\t}\n" +
                          "}\n";

            Files.write(Paths.get(pathToResultConf), conf.getBytes());

        } catch (Exception e) {
            printer.print("Error: unable to open config file.");
        }
    }
}
