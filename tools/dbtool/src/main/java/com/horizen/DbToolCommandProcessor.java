package com.horizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.storage.Storage;
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import com.horizen.tools.utils.Command;
import com.horizen.tools.utils.CommandProcessor;
import com.horizen.tools.utils.MessagePrinter;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;
import org.apache.logging.log4j.Logger;
import java.io.*;
import java.util.List;

public class DbToolCommandProcessor extends CommandProcessor {

    private final String  dataDirAbsolutePath;
    private final Logger log;


    public DbToolCommandProcessor(MessagePrinter printer, String dataDirAbsolutePath, Logger log) {
        super(printer);
        this.dataDirAbsolutePath = dataDirAbsolutePath;
        this.log = log;
    }

    @Override
    public void processCommand(String input) throws IOException {
        com.horizen.tools.utils.Command command = parseCommand(input);

        switch(command.name()) {
            case "help":
                printUsageMsg();
                break;
            case "lastVersionID":
                processLastVersionID(command.data());
                break;

            case "rollback":
                processRollback(command.data());
                break;

            case "versionsList":
                processVersionsList(command.data());
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
                "\tFrom command line: <program name> <storages data dir path> <custom storage names list> <command name> [<json data>]\n" +
                "\tFor interactive mode: <command name> [<json data>]\n" +
                "\tRead command arguments from file: <command name> -f <path to file with json data>\n" +
                "Supported commands:\n" +
                      "\thelp\n" +
                "\tlastVersionID <arguments>\n" +
                "\trollback <arguments>\n" +
                "\tversionsList <arguments>\n" +
                "\texit\n"
        );
    }

    private File getStorageFile(JsonNode json) throws IllegalArgumentException, FileNotFoundException {
        if (!json.has("storage") || !json.get("storage").isTextual()) {
            throw new IllegalArgumentException("storage is not specified or is not a string");
        }
        String storage_to_rollback = json.get("storage").asText();
        if (!DbTool.storageNames.contains(storage_to_rollback)) {
            throw new IllegalArgumentException(storage_to_rollback + " is not a valid storage type.");
        }
        log.info(json.toString());
        File storageFile = new File(dataDirAbsolutePath + File.separator + storage_to_rollback);

        if (!storageFile.exists()) {
            log.error("File on path[" + storageFile + "] doesn't exist");
            throw new FileNotFoundException("No such file: " + storageFile);
        }
        return storageFile;
    }

    private void printRollbackUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\trollback {\"storage\":\"<name>\", \"versionToRollback\":<version>}");
    }

    private void processRollback(JsonNode json) {
        if (!json.has("versionToRollback") || !json.get("versionToRollback").isTextual()) {
            printRollbackUsageMsg("versionToRollback is not specified or is not a string.");
            return;
        }
        String versionToRollback = json.get("versionToRollback").asText();
        ByteArrayWrapper version = new ByteArrayWrapper(BytesUtils.fromHexString(versionToRollback));

        // storage extends autocloseable
        try (Storage storage = new VersionedLevelDbStorageAdapter(getStorageFile(json))){

            String storageVersionPre  = BytesUtils.toHexString(storage.lastVersionID().get().data());
            storage.rollback(version);
            String storageVersionPost = BytesUtils.toHexString(storage.lastVersionID().get().data());

            ObjectNode resJson = new ObjectMapper().createObjectNode();
            resJson.put("storage", json.get("storage").asText());
            resJson.put("versionPrevious", storageVersionPre);
            resJson.put("versionCurrent", storageVersionPost);

            String res = resJson.toString();
            printer.print(res);

        } catch (IllegalArgumentException e) {
            // for instance when trying to roll back to a not-existing version
            printRollbackUsageMsg("Error in processing the command: " + e);
        }  catch (Exception e) {
            log.error("Error in processing the command: " + e);
        }
    }

    private void printVersionsListUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tversionsList {\"storage\":\"<name>\", \"numberOfVersionToRetrieve\":<int>}");
    }

    private void processVersionsList(JsonNode json) {
        if (!json.has("numberOfVersionToRetrieve") || !json.get("numberOfVersionToRetrieve").isInt()) {
            printVersionsListUsageMsg("numberOfVersionToRetrieve is not specified or is not an int.");
            return;
        }
        int numOfVersions = json.get("numberOfVersionToRetrieve").asInt();

        // storage extends autocloseable
        try (Storage storage = new VersionedLevelDbStorageAdapter(getStorageFile(json))){

            List<ByteArrayWrapper> bawList = storage.rollbackVersions();
            log.info(bawList);

            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            ObjectNode resJson = mapper.createObjectNode();

            resJson.put("storage", json.get("storage").asText());

            ArrayNode keyArrayNode = resJson.putArray("versionsList");

            for (ByteArrayWrapper e : bawList) {
                keyArrayNode.add(BytesUtils.toHexString(e.data()));
                if (numOfVersions > 0 && keyArrayNode.size() >= numOfVersions) {
                    break;
                }
            }

            String res = resJson.toString();
            printer.print(res);

        } catch (IllegalArgumentException e) {
            printVersionsListUsageMsg("Error in processing the command: " + e);
        }  catch (Exception e) {
            log.error("Error in processing the command: " + e);
        }
    }

    private void printLastVersionIDUsageMsg(String error) {
        printer.print("Error: " + error);
        printer.print("Usage:\n" +
                "\tlastVersionID {\"storage\":\"<name>\"}");
    }

    private void processLastVersionID(JsonNode json) {

        // storage extends autocloseable
        try (Storage storage = new VersionedLevelDbStorageAdapter(getStorageFile(json))){
            var optLastVer = storage.lastVersionID();
            if (optLastVer.isEmpty())
                throw new IllegalArgumentException("Selected DB is not versioned: " + getStorageFile(json));

            String storageVersion  = BytesUtils.toHexString(optLastVer.get().data());
            log.info(storageVersion);

            ObjectNode resJson = new ObjectMapper().createObjectNode();
            resJson.put("storage", json.get("storage").asText());
            resJson.put("version", storageVersion);

            String res = resJson.toString();
            printer.print(res);

        } catch (IllegalArgumentException e) {
            printLastVersionIDUsageMsg("Error in processing the command: " + e);
        }  catch (Exception e) {
            log.error("Error in processing the command: " + e);
        }
    }
}
