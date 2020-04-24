package com.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.block.MainchainBlockReference;
import com.horizen.block.MainchainHeader;
import com.horizen.block.Ommer;
import com.horizen.block.SidechainBlock;
import com.horizen.box.NoncedBox;
import com.horizen.companion.SidechainBoxesDataCompanion;
import com.horizen.companion.SidechainProofsCompanion;
import com.horizen.companion.SidechainTransactionsCompanion;
import com.horizen.params.MainNetParams;
import com.horizen.params.NetworkParams;
import com.horizen.params.RegTestParams;
import com.horizen.params.TestNetParams;
import com.horizen.proposition.Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.secret.PrivateKey25519Serializer;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.transaction.mainchain.SidechainCreation;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.VarInt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

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
            printGenerateKeyUsageMsg("wrong arguments syntax.");
            return;
        }
        PrivateKey25519 key = PrivateKey25519Creator.getInstance().generateSecret(json.get("seed").asText().getBytes());

        ObjectNode resJson = new ObjectMapper().createObjectNode();
        resJson.put("secret", BytesUtils.toHexString(key.bytes()));
        resJson.put("publicKey", BytesUtils.toHexString(key.publicImage().bytes()));

        String res = resJson.toString();
        printer.print(res);
    }

    private void printGenesisInfoUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                        "\tgenesisinfo {\n" +
                            "\t\t\"secret\": <secret hex>, - private key to sign the sc genesis block\n" +
                            "\t\t\"info\": <sc genesis info hex> - hex data retrieved from MC RPC call 'getscgenesisinfo'\n" +
                            "\t\t\"updateconfig\": boolean - Optional. Default false. If true, put the results in a copy of source config.\n" +
                            "\t\t\"sourceconfig\": <path to in config file> - expected if 'updateconfig' = true.\n" +
                            "\t\t\"resultconfig\": <path to out config file> - expected if 'updateconfig' = true.\n" +

                        "\t}"
        );
        printer.print("Examples:\n" +
                        "\tgenesisinfo {\"secret\":\"78fa...e818\", \"info\":\"0001....ad11\"}\n\n" +
                        "\tgenesisinfo {\"secret\":\"78fa...e818\", \"info\":\"0001....ad11\", \n" +
                            "\t\t\"updateconfig\": true, \"sourceconfig\":\"./template.conf\", \"resultconfig\":\"./result.conf\"}");
    }

    private void processGenesisInfo(JsonNode json) {
        if(!json.has("info") || !json.get("info").isTextual()
                || !json.has("secret") || !json.get("secret").isTextual()) {
            printGenesisInfoUsageMsg("wrong arguments syntax.");
            return;
        }

        String infoHex = json.get("info").asText();
        byte infoBytes[];
        try {
            infoBytes = BytesUtils.fromHexString(infoHex);
        } catch (IllegalArgumentException e) {
            printGenesisInfoUsageMsg("'info' expected to be a hex string.");
            return;
        }

        String secretHex = json.get("secret").asText();
        if(secretHex.length() != 128) {// size of hex representation of Private25519Key
            printGenesisInfoUsageMsg("'secret' value size is wrong.");
            return;
        }
        byte secretBytes[];
        try {
            secretBytes = BytesUtils.fromHexString(secretHex);
        } catch (IllegalArgumentException e) {
            printGenesisInfoUsageMsg("'secret' expected to be a hex string.");
            return;
        }

        PrivateKey25519 key = PrivateKey25519Serializer.getSerializer().parseBytes(secretBytes);

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

            SidechainBlock sidechainBlock = SidechainBlock.create(
                    params.sidechainGenesisBlockParentId(),
                    System.currentTimeMillis() / 1000,
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(mcRef.data())).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<SidechainTransaction<Proposition, NoncedBox<Proposition>>>()).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(mcRef.header())).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<Ommer>()).asScala().toSeq(),
                    key,
                    null,
                    null,
                    null,
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
                return new MainNetParams(scId, null, null, null, 1, 0,100, 120, 720, null, 0, null, null);
            case 1: // testnet
                return new TestNetParams(scId, null, null, null, 1, 0, 100, 120, 720, null, 0, null, null);
            case 2: // regtest
                return new RegTestParams(scId, null, null, null, 1, 0, 100, 120, 720, null, 0, null, null);
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
