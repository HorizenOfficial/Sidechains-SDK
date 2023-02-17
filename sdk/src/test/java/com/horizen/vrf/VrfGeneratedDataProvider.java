package com.horizen.vrf;

import com.google.common.base.Throwables;
import com.google.common.primitives.Ints;
import com.horizen.fixtures.VrfGenerator;
import com.horizen.proof.VrfProof;
import com.horizen.proposition.VrfPublicKey;
import com.horizen.secret.VrfKeyGenerator;
import com.horizen.secret.VrfSecretKey;
import com.horizen.utils.BytesUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class VrfGeneratedDataProvider {
    private static final String pathPrefix = "src/test/resources/";
    private static final ClassLoader classLoader = VrfGeneratedDataProvider.class.getClassLoader();

    public static VrfSecretKey getVrfSecretKey(Integer seed) {
        return VrfKeyGenerator.getInstance().generateSecret(Ints.toByteArray(seed));
    }

    public static VrfPublicKey getVrfPublicKey(Integer seed) {
        return VrfKeyGenerator.getInstance().generateSecret(seed.toString().getBytes(StandardCharsets.UTF_8)).publicImage();
    }

    public static VrfOutput getVrfOutput(Integer seed) {
        return VrfGenerator.generateVrfOutput(seed);
    }

    // vrf proof is not deterministic, even if the same parameters are passed to the creation method,
    // there is an internal random component in its generation
    public static void updateVrfProof(String prefix, Integer seed) {
        VrfProof obj = VrfGenerator.generateProof(seed);
        writeToFile(prefix, seed, obj.getClass(), obj.bytes());
    }

    public static VrfProof getVrfProof(String prefix, Integer seed) {
        return readFromFile(prefix, seed, VrfProof.class);
    }

    private static <T> String getFileName(String prefix, Integer seed, Class<T> classToProcess) {
        return prefix + classToProcess.getSimpleName() + seed.toString();
    }

    private static <T> void writeToFile(String prefix, Integer seed, Class<T> classToWrite, byte[] data) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(pathPrefix + getFileName(prefix, seed, classToWrite)));
            writer.write(BytesUtils.toHexString(data));
            writer.close();
        }
        catch (Throwable e) {
        }
    }

    private static<T> T readFromFile(String prefix, Integer seed, Class<T> classToRead) {
        byte[] bytes = readBytesFromFile(prefix, seed, classToRead);
        try {
            return (T) classToRead.getDeclaredConstructors()[0].newInstance(bytes);
        }
        catch (Exception e) {
            throw new RuntimeException(Throwables.getStackTraceAsString (e));
        }
    }

    private static<T> byte[] readBytesFromFile(String prefix, Integer seed, Class<T> classToRead) {
        try {
            String fileName = getFileName(prefix, seed, classToRead);
            URL url = classLoader.getResource(fileName);
            if (url == null) {
                throw new IllegalStateException("Failed to find file " + fileName + " probably it was not added to test resources in POM");
            }

            FileReader fileReader = new FileReader(url.getFile());
            byte[] bytes = BytesUtils.fromHexString(new BufferedReader(fileReader).readLine());
            return bytes;
        }
        catch (Exception e) {
            throw new RuntimeException(Throwables.getStackTraceAsString (e));
        }
    }

}
