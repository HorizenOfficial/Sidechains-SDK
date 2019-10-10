package com.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.block.MainchainBlockReference;
import com.horizen.block.SidechainBlock;
import com.horizen.box.NoncedBox;
import com.horizen.companion.SidechainTransactionsCompanion;
import com.horizen.params.MainNetParams;
import com.horizen.params.NetworkParams;
import com.horizen.params.RegTestParams;
import com.horizen.proposition.Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.secret.PrivateKey25519Serializer;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.VarInt;
import javafx.util.Pair;

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
        printer.print("Usage:\n\t" + "<command name> [<json data>]\nSupported commands:\n\thelp\n\tgeneratekey <arguments>\n\tgenesisinfo <arguments>\n\texit\n");
    }

    private void printUnsupportedCommandMsg(String command) {
        printer.print(String.format("Error: unsupported command '%s'.\nSee 'help' for usage guideline.", command));
    }

    private void printGenerateKeyUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n\tgeneratekey {\"seed\":\"my seed\"}");
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
        printer.print("Usage:\n\tgenesisinfo {\n\t\t" +
                "\"secret\" : \"<secret hex>, - private key to sign the sc genesis block\n\t\t" +
                "\"info\":\"sc genesis info\" - hex data retrieved from MC RPC call 'getscgenesisinfo'\n\t" +
                "}");
        printer.print("Example:\n\tgenesisinfo {\"secret\":\"78fa...e818\", \"info\"0001....ad11\"}");
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
            printer.print("Error: 'info' expected to be a hex string.");
            return;
        }

        String secretHex = json.get("secret").asText();
        if(secretHex.length() != 128) {// size of hex representation of Private25519Key
            printer.print("Error: 'secret' value size is wrong.");
            return;
        }
        byte secretBytes[];
        try {
            secretBytes = BytesUtils.fromHexString(secretHex);
        } catch (IllegalArgumentException e) {
            printer.print("Error: 'secret' expected to be a hex string.");
            return;
        }

        PrivateKey25519 key = PrivateKey25519Serializer.getSerializer().parseBytes(secretBytes);


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


            NetworkParams params = getNetworkParams(network, scId);
            MainchainBlockReference mcRef = MainchainBlockReference.create(Arrays.copyOfRange(infoBytes, offset, infoBytes.length), params).get();


            SidechainTransactionsCompanion sidechainTransactionsCompanion = new SidechainTransactionsCompanion(new HashMap<>());

            String sidechainBlockHex = BytesUtils.toHexString(SidechainBlock.create(
                    SidechainSettings.genesisParentBlockId(),
                    System.currentTimeMillis() / 1000,
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(mcRef)).asScala().toSeq(),
                    scala.collection.JavaConverters.collectionAsScalaIterableConverter(new ArrayList<SidechainTransaction<Proposition, NoncedBox<Proposition>>>()).asScala().toSeq(),
                    key,
                    sidechainTransactionsCompanion,
                    params,
                    scala.Option.empty()
            ).get().bytes());


            // TO DO: add arguments to request to update the put the results to some config file
            // or even create another method for that.
            updateTemplateFile("src/main/resources/settings.conf",
                    "src/main/resources/sc_settings.conf",
                    mcBlockHeight,
                    powData,
                    BytesUtils.toHexString(scId),
                    sidechainBlockHex);
            ObjectNode resJson = new ObjectMapper().createObjectNode();
            resJson.put("scId", BytesUtils.toHexString(scId));
            resJson.put("scGenesisBlock", sidechainBlockHex);

            String res = resJson.toString();
            printer.print(res);
        } catch (Exception e) {
            printer.print("Error: 'info' data is corrupted.");
        }
    }

    private NetworkParams getNetworkParams(byte network, byte[] scId) {
        switch(network) {
            case 0: // mainnet
            case 1: // testnet
                return new MainNetParams(scId, null);
            case 2: // regtest
                return new RegTestParams(scId, null);
        }
        return null;

    }

    private void updateTemplateFile(
            String pathToTemplate,
            String pathToResultConf,
            int mcBlockHeight,
            String powData,
            String scId,
            String scBlockHex) {

        try {
            String templateConf = new String(Files.readAllBytes(Paths.get(pathToTemplate)), StandardCharsets.UTF_8);


            String conf = templateConf +
                    "\nscorex {\n\t" +
                    "genesis {\n\t\t" +
                    "scGenesisBlockHex = \"" + scBlockHex + "\"\n\t\t" +
                    "scId = \"" + scId + "\"\n\t\t" +
                    "powData = \"" + powData + "\"\n\t\t" +
                    "mcBlockHeight = " + mcBlockHeight + "\n\t" +
                    "}\n" +
                    "}\n";

            Files.write(Paths.get(pathToResultConf), conf.getBytes());

        } catch (Exception e) {
            printer.print("Error: unable to create read template conf file.");
        }
    }
}
