package com.horizen.tools.utils;

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
import com.horizen.box.Box;
import com.horizen.box.ForgerBox;
import com.horizen.companion.SidechainSecretsCompanion;
import com.horizen.companion.SidechainTransactionsCompanion;
import com.horizen.consensus.ForgingStakeInfo;
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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public abstract class CommandProcessor {
    protected MessagePrinter printer;
    public CommandProcessor(MessagePrinter printer) {
        this.printer = printer;
    }

    protected abstract void processCommand(String input) throws IOException;

    protected abstract Command parseCommand(String input) throws IOException;

    protected abstract void printUsageMsg();

    protected void printUnsupportedCommandMsg(String command) {
        printer.print(String.format("Error: unsupported command '%s'.\nSee 'help' for usage guideline.", command));
    }

}
