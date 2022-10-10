package com.horizen;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.schnorrnative.SchnorrKeyPair;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSecretKey;
import org.junit.Before;
import org.junit.Test;
import scorex.util.encode.Base64;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static junit.framework.TestCase.*;


public class SigningToolTest {

    public static final String SCHNORR = "schnorr";
    private ByteArrayOutputStream byteArrayOutputStream;
    private PrintStream console;

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
        }
        finally {
            System.setOut(console);
        }
        return null;
    }

    @Test
    public void testUsage() {
        String command = "help";
        String result = runTest(new String[]{command});
        assertTrue(result != null && result.contains("Usage:") && result.contains("Supported commands:"));
    }

    @Test
    public void testSignatureSignVerify() throws JsonProcessingException {
        String command = "createSignature";
        String testMessage = "Test message to sign/verify.";
        SchnorrKeyPair keyPair = SchnorrKeyPair.generate();
        SchnorrSecretKey secretKey = keyPair.getSecretKey();
        SchnorrPublicKey publicKey = keyPair.getPublicKey();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", testMessage);
        argsJson.put("privateKey", secretKey.serializeSecretKey());
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("signature") && !result.contains("error"));

        command = "verifySignature";
        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String signature = signatureJson.get("signature").textValue();
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", testMessage);
        argsJson.put("signature", signature);
        argsJson.put("publicKey", publicKey.serializePublicKey());
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("valid") && result.contains("true"));
    }

    @Test
    public void testMessageSignVerify() throws JsonProcessingException {
        String command = "signMessage";
        String testMessage = "Message to sign/verify.";
        String prefix = "prefix";
        SchnorrKeyPair keyPair = SchnorrKeyPair.generate();
        SchnorrSecretKey secretKey = keyPair.getSecretKey();
        SchnorrPublicKey publicKey = keyPair.getPublicKey();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", testMessage);
        argsJson.put("privateKey", secretKey.serializeSecretKey());
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("signature") && !result.contains("error"));

        command = "validateMessage";
        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String signature = signatureJson.get("signature").textValue();
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", testMessage);
        argsJson.put("signature", signature);
        argsJson.put("publicKey", publicKey.serializePublicKey());
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("valid") && result.contains("true"));
    }

    @Test
    public void testPrivKeyToPubkey() throws JsonProcessingException {
        String command = "privKeyToPubkey";
        SchnorrKeyPair keyPair = SchnorrKeyPair.generate();
        SchnorrSecretKey secretKey = keyPair.getSecretKey();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("privateKey", Base64.encode(secretKey.serializeSecretKey()));
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{command, args});
        assertTrue(result != null && result.contains("publicKey") && !result.contains("error"));

        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String publicKey = signatureJson.get("publicKey").textValue();

        assertEquals(Base64.encode(keyPair.getPublicKey().serializePublicKey()), publicKey);
    }

}
