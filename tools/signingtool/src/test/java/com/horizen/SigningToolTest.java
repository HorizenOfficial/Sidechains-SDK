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
    public static final String CREATE_SIGNATURE = "createSignature";
    public static final String VERIFY_SIGNATURE = "verifySignature";
    public static final String SIGN_MESSAGE = "signMessage";
    public static final String VALIDATE_MESSAGE = "validateMessage";
    public static final String PRIV_KEY_TO_PUB_KEY = "privKeyToPubKey";
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
            byteArrayOutputStream.reset();
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
        String testMessage = "Test signmessage";
        SchnorrSecret secretKey = generateSecret();
        SchnorrProposition publicKey = secretKey.publicImage();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString(testMessage.getBytes()));
        argsJson.put("privateKey", BytesUtils.toHexString(SECRETS_COMPANION.toBytes(secretKey)));
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{CREATE_SIGNATURE, args});
        assertTrue(result != null && result.contains("signature") && !result.contains("error"));

        //Verify
        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String signature = signatureJson.get("signature").textValue();
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString(testMessage.getBytes()));
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{VERIFY_SIGNATURE, args});
        assertTrue(result != null && result.contains("valid") && result.contains("true"));

        //Negative
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString("Wrong message".getBytes()));
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{VERIFY_SIGNATURE, args});
        assertTrue(result != null && result.contains("valid") && result.contains("false"));

        //Invalid signature
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString(testMessage.getBytes()));
        argsJson.put("signature", signature + "123");
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{VERIFY_SIGNATURE, args});
        assertTrue(result != null && result.contains("error") && result.contains("Unable to parse signature"));

        //Invalid publicKey
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString(testMessage.getBytes()));
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)) + "123");
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{VERIFY_SIGNATURE, args});
        assertTrue(result != null && result.contains("error") && result.contains("Unable to parse publicKey"));
    }

    @Test
    public void testMessageSignVerify() throws JsonProcessingException {
        //Sign
        String testMessage = "Message to sign/verify.";
        String prefix = "prefix";
        SchnorrSecret secretKey = generateSecret();
        SchnorrProposition publicKey = secretKey.publicImage();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString(testMessage.getBytes()));
        argsJson.put("privateKey", BytesUtils.toHexString(SECRETS_COMPANION.toBytes(secretKey)));
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{SIGN_MESSAGE, args});
        assertTrue(result != null && result.contains("signature") && !result.contains("error"));

        //Verify
        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String signature = signatureJson.get("signature").textValue();
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString(testMessage.getBytes()));
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{VALIDATE_MESSAGE, args});
        assertTrue(result != null && result.contains("valid") && result.contains("true"));

        //Negative
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString("Wrong message".getBytes()));
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{VALIDATE_MESSAGE, args});
        assertTrue(result != null && result.contains("valid") && result.contains("false"));

        //Invalid signature
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString(testMessage.getBytes()));
        argsJson.put("signature", signature + "123");
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)));
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{VALIDATE_MESSAGE, args});
        assertTrue(result != null && result.contains("error") && result.contains("Unable to parse signature"));

        //Invalid publicKey
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("message", BytesUtils.toHexString(testMessage.getBytes()));
        argsJson.put("signature", signature);
        argsJson.put("publicKey", BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(publicKey)) + "123");
        argsJson.put("prefix", prefix);
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();
        result = runTest(new String[]{VALIDATE_MESSAGE, args});
        assertTrue(result != null && result.contains("error") && result.contains("Unable to parse publicKey"));
    }

    private SchnorrSecret generateSecret() {
        Random rnd = new Random();
        byte[] vrfSeed = new byte[32];
        rnd.nextBytes(vrfSeed);
        return SchnorrKeyGenerator.getInstance().generateSecret(vrfSeed);
    }

    @Test
    public void testPrivKeyToPubkey() throws IOException {
        SchnorrSecret secretKey = generateSecret();

        ObjectNode argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("privateKey", BytesUtils.toHexString(SECRETS_COMPANION.toBytes(secretKey)));
        argsJson.put("type", SCHNORR);
        String args = argsJson.toString();

        String result = runTest(new String[]{PRIV_KEY_TO_PUB_KEY, args});
        assertTrue(result != null && result.contains("publicKey") && !result.contains("error"));

        JsonNode signatureJson = new ObjectMapper().readTree(result);
        String publicKey = signatureJson.get("publicKey").asText();

        assertEquals(BytesUtils.toHexString(PROPOSITION_SERIALIZER.toBytes(secretKey.publicImage())), publicKey);

        //Invalid privateKey
        argsJson = new ObjectMapper().createObjectNode();
        argsJson.put("privateKey", BytesUtils.toHexString(SECRETS_COMPANION.toBytes(secretKey)) + "123");
        argsJson.put("type", SCHNORR);
        args = argsJson.toString();

        result = runTest(new String[]{PRIV_KEY_TO_PUB_KEY, args});
        assertTrue(result != null && result.contains("error") && result.contains("Unable to parse privateKey"));
    }

}
