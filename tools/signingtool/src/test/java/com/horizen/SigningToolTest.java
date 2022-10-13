package com.horizen;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.companion.SidechainSecretsCompanion;
import com.horizen.proposition.SchnorrProposition;
import com.horizen.proposition.SchnorrPropositionSerializer;
import com.horizen.secret.SchnorrKeyGenerator;
import com.horizen.secret.SchnorrSecret;
import com.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Random;

import static junit.framework.TestCase.*;


public class SigningToolTest {

    public static final String SCHNORR = "schnorr";
    private ByteArrayOutputStream byteArrayOutputStream;
    private PrintStream console;
    public static final SidechainSecretsCompanion SECRETS_COMPANION = new SidechainSecretsCompanion(new HashMap<>());
    public static final SchnorrPropositionSerializer PROPOSITION_SERIALIZER = SchnorrPropositionSerializer.getSerializer();

    @Before
    public void setup() {
        byteArrayOutputStream = new ByteArrayOutputStream();
        console = System.out;
    }

    private String runTest(final String[] args) {
        try {
            System.setOut(new PrintStream(byteArrayOutputStream));
            SigningTool.main(args);
            return byteArrayOutputStream.toString();
        } catch (Exception e) {
            fail("Unexpected error in tests: " + e.getMessage());
        } finally {
            System.setOut(console);
        }
        return null;
    }

    @Test
    public void testUsage() {
        String command = "help";
        String result = runTest(new String[]{command});
        assertTrue(result != null && result.contains("Usage") && result.contains("Supported commands"));
    }

    @Test
    public void testSignatureSignVerify() throws JsonProcessingException {
        //Sign
        String command = "createSignature";
        String testMessage = "Test message to sign/verify.";
        SchnorrSecret secretKey = generateSecret();
        SchnorrProposition publicKey = secretKey.publicImage();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", testMessage);
        argsJson.put("privateKey", BytesUtils.toHexString(SECRETS_COMPANION.toBytes(secretKey)));
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("signature") && !result.contains("error"));

        //Verify
        command = "verifySignature";
        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String signature = signatureJson.get("signature").textValue();
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", testMessage);
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("valid") && result.contains("true"));

        //Negative
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", "Wrong Message!");
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("valid") && result.contains("false"));
    }

    @Test
    public void testMessageSignVerify() throws JsonProcessingException {
        //Sign
        String command = "signMessage";
        String testMessage = "Message to sign/verify.";
        String prefix = "prefix";
        SchnorrSecret secretKey = generateSecret();
        SchnorrProposition publicKey = secretKey.publicImage();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", testMessage);
        argsJson.put("privateKey", BytesUtils.toHexString(SECRETS_COMPANION.toBytes(secretKey)));
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("signature") && !result.contains("error"));

        //Verify
        command = "validateMessage";
        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String signature = signatureJson.get("signature").textValue();
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", testMessage);
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("valid") && result.contains("true"));

        //Negative
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", "Wrong message!");
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("valid") && result.contains("false"));
    }

    private SchnorrSecret generateSecret() {
        Random rnd = new Random();
        byte[] vrfSeed = new byte[32];
        rnd.nextBytes(vrfSeed);
        return SchnorrKeyGenerator.getInstance().generateSecret(vrfSeed);
    }

    @Test
    public void testPrivKeyToPubkey() throws IOException {
        String command = "privKeyToPubKey";
        SchnorrSecret secretKey = generateSecret();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("privateKey", BytesUtils.toHexString(SECRETS_COMPANION.toBytes(secretKey)));
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("publicKey") && !result.contains("error"));

        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String publicKey = signatureJson.get("publicKey").asText();

        assertEquals(BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(secretKey.publicImage())), publicKey);
    }

}
