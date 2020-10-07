package com.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.block.MainchainBlockReference;
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
import com.horizen.proof.VrfProof;
import com.horizen.proposition.Proposition;
import com.horizen.secret.*;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.transaction.mainchain.SidechainCreation;
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.MerklePath;
import com.horizen.utils.VarInt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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
            case "generateProofInfo":
                processGenerateProofInfo(command.data());
                break;
            default:
                printUnsupportedCommandMsg(command.name());
        }
    }

    // Command structure is:
    // 1) <command name>
    // 1) <command name> <json argument>
    // 2) <command name> -f <path to file with json argument>
    private Command parseCommand(String input) throws IOException {
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

    private void printUsageMsg() {
        printer.print("Usage:\n" +
                      "\tFrom command line: <program name> <command name> [<json data>]\n" +
                      "\tFor interactive mode: <command name> [<json data>]\n" +
                      "\tRead command arguments from file: <command name> -f <path to file with json data>\n" +
                      "Supported commands:\n" +
                      "\thelp\n" +
                      "\tgeneratekey <arguments>\n" +
                      "\tgenerateVrfKey <arguments>\n" +
                      "\tgenerateProofInfo <arguments>\n" +
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

    private void printGenerateProofInfoUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                      "\tgenerateProofInfo {\"seed\":\"my seed\", \"keyCount\":7, \"threshold\":5}" +
                      "\tthreshold parameter should be less or equal to keyCount.");
    }

    private void processGenerateProofInfo(JsonNode json) {

        if(!json.has("seed") || !json.get("seed").isTextual()) {
            printGenerateProofInfoUsageMsg("seed is not specified or has invalid format.");
            return;
        }

        if (!json.has("keyCount") || !json.get("keyCount").isInt()) {
            printGenerateProofInfoUsageMsg("wrong key count");
            return;
        }

        int keyCount = json.get("keyCount").asInt();

        if (keyCount <= 0) {
            printGenerateProofInfoUsageMsg("wrong key count - " + keyCount);
            return;
        }

        if (!json.has("threshold") || !json.get("threshold").isInt()) {
            printGenerateProofInfoUsageMsg("wrong threshold");
            return;
        }

        int threshold = json.get("threshold").asInt();

        if (threshold <= 0 || threshold > keyCount) {
            printGenerateProofInfoUsageMsg("wrong threshold - " + threshold);
            return;
        }

        //@TODO remove hardcoded value after supporting various verification/proving files, actual file is generated for 7 keys
        if (keyCount != 7) {
            printGenerateProofInfoUsageMsg("Currently supported only 7 keys");
            return;
        }

        SidechainSecretsCompanion secretsCompanion = new SidechainSecretsCompanion(new HashMap<>());

        List<SchnorrSecret> secretKeys = new ArrayList<>();

        for (int i = 0; i < keyCount; i++ ) {
            secretKeys.add(SchnorrKeyGenerator.getInstance()
                    .generateSecret(Bytes.concat(json.get("seed").asText().getBytes(), Ints.toByteArray(i))));
        }

        List<byte[]> publicKeysBytes = secretKeys.stream().map(SchnorrSecret::getPublicBytes).collect(Collectors.toList());
        String genSysConstant = BytesUtils.toHexString(CryptoLibProvider.sigProofThresholdCircuitFunctions().generateSysDataConstant(publicKeysBytes, threshold));

        //valid for 7 keys with threshold 5
        String verificationKey = "5e7b462cc84ae0faaa5884bd5c4a5a5edf13db210599aeeb4d273c0f5f32967b7071ce2b4d490b9f08f6ce66a8405735c79197cd6773d1c5aeb2a38da1c102df07b05879c77198e5aafa7feed25d4137e86b3d98d9edd9547a460f1615b10000ee9570fbffedd44170477b37500a0a1cb3f94b6361f10f8a68c4075fbc17542d7174b3d95e12ddb8aea5d6b6c53c1df6c8f60010cd2e69902ba5e89e86747569463a23254730fc8d2aabf39648a505df9dcce461443b181ef3eda46074070000550836db2c97820971db6b1421e348d946ed4d3f255295abea46556615e3123de33ec56f784f70302901a4bc10c79c6a8b1e32477aeff9fba75876592981b678fc5a2703ac0b3055e567a6cb1ebab578fc4f9121fd968680250696cb85790000078fcfb60bdfc79aa1e377cb120480538e0236156f23129a88824ca5a1d77e371e5e98a16e6f32087c91aa02a4f5e00e412e515c3b678f6535141203c6886c637b626a2ada4062d037503359a680979091c68941a307db6e4ed8bc49d21b00002f0e6f88fb69309873fdefb015569e5511fb5399295204876543d065d177bf36ab79183a7c5e504b50691bc5b4ed0293324cfe2555d3fc8e39485822a90a91afcd4ef79ec3aefbd4cbe25cbccd802d8334ce1dce238c3f7505330a14615500001f89fbe1922ab3aa31a28fd29e19673714a7e48050dee59859d68345bb7bee7d5e888d8b798a58d7c650f9138304c05a92b668294c6114185ccb2c67ce0bbbb7e1dcbb6d76f5cacd7c9732a33b21d69bd7a28c9cca68b5735d50413862bc0100308bb0dd0bd53f3d1134966702dd3c7cc8b58b270a6996a646493250b0d5f3978d0c971f8fa7a0c958f3efe2fa5269244973fafb701c2eb66dd25901f93d677ab6c538c1ed11f115e52d3f2c7087ea40c3e8cd089376baa38842e9429b5f0000d19a8d874d791f952f13d3c8ecd92e44009c09815e5ae6a8e5def7ea52fe3de4accfb5ba2aa401fbcec14b069cd0dc0f66ab025b45ef9831a26acf58673db7487043654e7980fcb2b6c1bd7593a4dfff810436f653e309121c7ccf2df70b010000732254ec6df184be360cd9ed383ed7c8c236d7761cfc0ce4e7f0cac5a06f4edab9cfc75a7dc1449c0e18ed9564c974c2e1b6847c637f74e5d391cbc80fc6e672ffd66b5ce4fb73bda8359ab8a0ea1e855df1e07d82f93c935c7e1a9a55c5000065efdbb7c3e82291a482b2f24cbd46f4dd02c370cf6dcfe8fb3c00b8b004b5ad51369b1f1b134a824d1f16d72ca6a27ba2d6190150329139cf2c6d9e5a14722f8d39b96b882c1f60a7b230e929819e2abe1cd9d7f3e8c726b1a94d20c8010100732c396eca6ffa1bf851cef449f2f087edd93e4f641b4bd93a482d9f129e675aedb688993d4e2cee824d2803301364ba10fbb66895927adb53bad8aefe8a1caab6f4ccb45883e414a1223ac7f90a89087cd752dfa0c7b3e19bbae000edd5000028d1d23c627d1252d2a2a20a246af2280f50e3fde667873aadd9893ba6833118358398e7428e717128f764714a8d52b090c1f554f58e25ea815338d7bc7326c949567e74f2f2ab3c88f5075fea75594608b8937c9059a42d712ffbd1bd980100000000000250c1a474689e375a309446e5cdd3a0c26cecdcff5c7b8cdc0728868983f1a35a49e3a1bae6f969c3d47356c08d3d169d2c0a2be908d82cd35f41a23d8c2924a9f790ab3a00d53061d440a176670d6a32de2ecd19cf8a9774729c09a6ea4d0100d8838bf55d95521291da12294b302c66042eda0dc2acc79360a1fdd8c9a366fa790c52bf926c2d96b5ba88a3a443487c5235f7c476f350c2101cfbe3bd0361dd291ebc5e42c097a158704b71006886a3662ca6db7d816b4ad12444835d89000000795ce2b34aef921ccb3d9b9695f5d3fe0a03743c955cfcf01f8a1815a7c8b03de85fe15201d4b4b6f401cb334a6988ea5bde8986a468c47c3c6a5ae96a3160ff15e06699ea82bd40c0d5547fe1be77af7817861bbfcca3f4232f05a9cec800006c216565cee4d57b32d2d70bb3cb8d4a967c0eb5d7137b2ec58466f3d4d3b5375e4baa823bcc29c6ad877d9708cd5dc1c31fa3883a80710431110c4aa22e97b67fa639f54e86cfab87187011270139df7873bed12f6fb8cd9ab48f389338010000";

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode resJson = mapper.createObjectNode();

        resJson.put("threshold", threshold);
        resJson.put("genSysConstant", genSysConstant);
        resJson.put("verificationKey", verificationKey);

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
        byte[] infoBytes;
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

            byte[] scId = BytesUtils.reverseBytes(Arrays.copyOfRange(infoBytes, offset, offset + 32));
            offset += 32;

            VarInt powDataLength = BytesUtils.getVarInt(infoBytes, offset);
            offset += powDataLength.size();

            String powData = BytesUtils.toHexString(Arrays.copyOfRange(infoBytes, offset, offset + ((int)powDataLength.value() * 8)));
            offset += powDataLength.value() * 8;

            int mcBlockHeight = BytesUtils.getReversedInt(infoBytes, offset);
            offset += 4;

            String mcNetworkName = getNetworkName(network);
            NetworkParams params = getNetworkParams(network, scId);
            // Uncomment if you want to save mc block hex for some reason
            /* try (PrintStream out = new PrintStream(new FileOutputStream("c:/mchex.txt"))) {
                out.print(BytesUtils.toHexString(Arrays.copyOfRange(infoBytes, offset, infoBytes.length)));
            }*/

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
            long timestamp = System.currentTimeMillis() / 1000 - (params.consensusSlotsInEpoch() / 2 * params.consensusSecondsInSlot());

            SidechainBlock sidechainBlock = SidechainBlock.create(
                    params.sidechainGenesisBlockParentId(),
                    timestamp,
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(Collections.singletonList(mcRef.data())).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<SidechainTransaction<Proposition, NoncedBox<Proposition>>>()).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(Collections.singletonList(mcRef.header())).asScala().toSeq(),
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
                return new MainNetParams(scId, null, null, null, null, 1, 0,100, 120, 720, null, 0, null, null, null);
            case 1: // testnet
                return new TestNetParams(scId, null, null, null, null, 1, 0, 100, 120, 720, null, 0, null, null, null);
            case 2: // regtest
                return new RegTestParams(scId, null, null, null, null, 1, 0, 100, 120, 720, null, 0, null, null, null);
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
