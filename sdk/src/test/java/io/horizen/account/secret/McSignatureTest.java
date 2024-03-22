package io.horizen.account.secret;

import com.google.common.primitives.Bytes;
import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.state.McAddrOwnershipMsgProcessor;
import io.horizen.account.utils.Secp256k1;
import io.horizen.params.RegTestParams;
import io.horizen.utils.BytesUtils;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import sparkz.util.encode.Base58;
import sparkz.util.encode.Base64;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.SignatureException;
import java.util.Arrays;

import static io.horizen.account.utils.BigIntegerUInt256.getUnsignedByteArray;
import static io.horizen.account.utils.Secp256k1.*;
import static io.horizen.utils.BytesUtils.padWithZeroBytes;
import static io.horizen.utils.BytesUtils.toHorizenPublicKeyAddress;
import static io.horizen.utils.Utils.Ripemd160Sha256Hash;
import static io.horizen.utils.Utils.doubleSHA256Hash;
import static org.junit.Assert.*;

public class McSignatureTest {

    @Test
    public void comp2uncompressedPubKey() {
        // In Bitcoin, a public key is composed of its (x, y) coordinates on the secp256k1 elliptic curve. Since the
        // curve’s equation is known (y2 = x3 + 7), given only x we can derive y or -y.
        // Therefore, we can cut the space taken by the public key in half by removing the y value (this is called
        // compression). Since Bitcoin’s curve is over a prime field, we can use y’s parity to distinguish between the
        // two square root solutions.

        // mc pub keys (like in bitcoin) are compressed, that is are 32 bytes prepended with parity byte, 0x3 in this case.
        // We can see the pub key of an address via the rpc command:
        // src/zen-cli --regtest validateaddress "ztTw2K532ewo9gynBJv7FFUgbD19Wpifv8G"
        byte[] mcPubKey = BytesUtils.fromHexString("039bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418f");
        assertEquals(1 + 32, mcPubKey.length);

        // get ecdsa curve y^2 mod p = (x^3 + 7) mod p
        var ecParameters = SECNamedCurves.getByName("secp256k1");

        // get the point on the curve corresponding to mc pub key
        ECPoint ecpoint = ecParameters.getCurve().decodePoint(mcPubKey);
        byte[] compressedPubKey = ecpoint.getEncoded(true);

        // assert we have our input back: mc pub key
        assertArrayEquals(mcPubKey, compressedPubKey);

        // get uncompressed form
        byte[] uncompressedPubKey = ecpoint.getEncoded(false);

        // assert we have the first byte set to the uncompressed byte (0x04)
        assertEquals(0x04, uncompressedPubKey[0]);
        assertEquals(1 + 64, uncompressedPubKey.length);

        System.out.println(BytesUtils.toHexString(compressedPubKey));
        System.out.println(BytesUtils.toHexString(uncompressedPubKey));
        /*
        compressed = 039bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418f
        uncompressed = 049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d4023
         */
    }

    @Test
    public void mcPrivKeyToScFormat() {
        /*
        zend rpc command for dumping the base58 encoded private key corresponding to a transparent address
                $ src/zen-cli --regtest dumpprivkey "ztTw2K532ewo9gynBJv7FFUgbD19Wpifv8G"
                cUHcJUdzjNceFfUhLQtrrbjPtHo5NBP9ZofNxdSFnQqXijao761c
         */
        String mcPrivKeyDump = "cUHcJUdzjNceFfUhLQtrrbjPtHo5NBP9ZofNxdSFnQqXijao761c";
        byte[] decodedGrossMcPrivKey = Base58.decode(mcPrivKeyDump).get();
        assertEquals(38, decodedGrossMcPrivKey.length);

        // we must extract the net payload. We have:
        // byte 0: 0xef = network parameter depending on network type, here is regtest (0x80 in mainnet)
        // byte 1-33: priv key
        // byte 34: 0x01 = Compressed flag
        // byte 35-38: 0x2ca679a9 = checksum (first 4 bytes of payload hash)
        byte[] mcPrivKey = Arrays.copyOfRange(decodedGrossMcPrivKey, 1, 33);

        System.out.println(BytesUtils.toHexString(mcPrivKey));
        assertEquals(
                "c811a42bbcf269cb551fa517d90431cffc16703184ab1adce14c7671efc61704",
                BytesUtils.toHexString(mcPrivKey)
        );

    }

    @Test
    public void mcPrivKeyToMcTaddr() {
        String mcPrivKeyDump = "cUHcJUdzjNceFfUhLQtrrbjPtHo5NBP9ZofNxdSFnQqXijao761c";
        byte[] decodedGrossMcPrivKey = Base58.decode(mcPrivKeyDump).get();
        byte[] mcPrivKey = Arrays.copyOfRange(decodedGrossMcPrivKey, 1, 33);
        byte[] uncompPubKeyBytes = Secp256k1.getPublicKey(mcPrivKey);
        var ecParameters = SECNamedCurves.getByName("secp256k1");

        // get the point on the curve corresponding to the uncompressed (0x04 prepended) mc pub key
        ECPoint ecpoint = ecParameters.getCurve().decodePoint(Bytes.concat(BytesUtils.fromHexString("04"), uncompPubKeyBytes));
        byte[] compPubKeyBytes = ecpoint.getEncoded(true);

        byte[] mcPubkeyhash = Ripemd160Sha256Hash(compPubKeyBytes);

        String computedTaddr = toHorizenPublicKeyAddress(
                mcPubkeyhash,
                new RegTestParams(null, null, null,
                        null, null, 0,
                        0, 0, null, null, null,
                        0, null, null,
                        null, null, null,
                        null, null, false, null,
                        null, 0, false, false,
                        false, 0, 2000, false, 0, Option.empty()));


        System.out.println(computedTaddr);
        assertEquals("ztTw2K532ewo9gynBJv7FFUgbD19Wpifv8G", computedTaddr);
    }

    @Test
    public void scPrivKeyToMcPubkeyFormat() {
        // see test above, we can have the hex string of the priv key via rpc dumppriv cmd and pub key via verifyaddress
        String scPrivKeyString = "c811a42bbcf269cb551fa517d90431cffc16703184ab1adce14c7671efc61704";
        String mcPubKeyString = "032a5bb6de7d7d39455c2ddce7a6c6e9149e9633985e2d384af0c2900814d8d6a6";
        byte[] scPrivKeyBytes = BytesUtils.fromHexString(scPrivKeyString);
        byte[] mcPubKeyBytes = BytesUtils.fromHexString(mcPubKeyString);

        PrivateKeySecp256k1 scPrivKey = PrivateKeySecp256k1Serializer.getSerializer().parseBytes(scPrivKeyBytes);

        byte[] uncompPubKeyBytes = Secp256k1.getPublicKey(scPrivKeyBytes);
        System.out.println(BytesUtils.toHexString(uncompPubKeyBytes));
        // uncompressed pubkey has both x and y-axis values, 2x32 bytes
        assertEquals(64, uncompPubKeyBytes.length);

        // address is the last keccak256 hash of the uncompressed pubkey
        byte[] eth_address_bytes = scPrivKey.publicImage().pubKeyBytes();
        System.out.println(BytesUtils.toHexString(eth_address_bytes));
        assertEquals(20, eth_address_bytes.length);

        var ecParameters = SECNamedCurves.getByName("secp256k1");

        // get the point on the curve corresponding to the uncompressed (0x04 prepended) mc pub key
        ECPoint ecpoint = ecParameters.getCurve().decodePoint(Bytes.concat(BytesUtils.fromHexString("04"), uncompPubKeyBytes));
        byte[] compPubKeyBytes = ecpoint.getEncoded(true);
        System.out.println(BytesUtils.toHexString(compPubKeyBytes));

        // check that we have the expected mc pub key, which is the compressed pub key (x-axis value with parity byte)
        assertArrayEquals(mcPubKeyBytes, compPubKeyBytes);

    }

    public byte[] getMcHashedMsg(String strMessageToSign) {
        // this reproduces the MC way of getting a message for signing it via rpc signmessage cmd

        // this is the magic string prepended in zend to the message to be signed*/
        String strMessageMagic = "Zcash Signed Message:\n";

        // compute the message to be signed. Similarly to what MC does, we must prepend the size of the byte buffers
        // we are using
        byte[] messageMagicBytes = strMessageMagic.getBytes(StandardCharsets.UTF_8);
        byte[] mmb2 = Bytes.concat(new byte[]{(byte) messageMagicBytes.length}, messageMagicBytes);
        byte[] messageToSignBytes = strMessageToSign.getBytes(StandardCharsets.UTF_8);
        byte[] mts2 = Bytes.concat(new byte[]{(byte) messageToSignBytes.length}, messageToSignBytes);

        // hash the message as MC does (double sha256)
        return doubleSHA256Hash(Bytes.concat(mmb2, mts2));
    }

    @Test
    public void testMcSignature() throws SignatureException {

        // for ripemd160 hash
        Security.addProvider(new BouncyCastleProvider());

        /*
          zend rpc command for getting the signature of an input message given a transparent address. The signature will
          be applied using the private key corresponding to the transparent address:

              $ src/zen-cli -regtest signmessage "ztdKq1uqVEfVjhJ16kyKEANmeJes1xfmpP1" "0x00c8f107a09cd4f463afc2f1e6e5bf6022ad4600"
              IOc348/la3bqCb31IQg0DEVOowg6f3cKll+SmZ6ip4KVR37qp0aG72iqZwdulDmj+1bid+IdIJJhoOm1kMqCX/s=



          zend rpc command for getting info about a transparent address, including its pub key

              $ src/zen-cli -regtest validateaddress "ztdKq1uqVEfVjhJ16kyKEANmeJes1xfmpP1"
              {
                "isvalid": true,
                "address": "ztdKq1uqVEfVjhJ16kyKEANmeJes1xfmpP1",
                "scriptPubKey": "76a9146f8ef406a53658c6d2341c61c8294f0b1a6d1aec88ac209c59a98e645f356c5da540fbd3c249c1e7278a60cef9052572ea111446e60200038c6c13b4",
                "ismine": true,
                "iswatchonly": false,
                "isscript": false,
                "pubkey": "039f6b8c47c7d66a7a3c67c523b49ee6c77af7d41d96cf74942c2c9ac24425e645",
                "iscompressed": true,
                "account": ""
              }

         */


        // see rpc cmds above. Note:
        // 1. we can not get the pub key from the transparent address because it is hashed before base58 encoding.
        //    And we can not get the taddr from the recovered address because it is hashed too (see
        //    mcAddressProposition below)
        // 2. here we have 33 bytes. The first byte 0x02/03 is a tag indicating the parity value of the compressed format
        String strMcPubKey = "039f6b8c47c7d66a7a3c67c523b49ee6c77af7d41d96cf74942c2c9ac24425e645";

        // in order to get an address proposition we must get the uncompressed format of the mc pub key from the
        // compressed one:
        // 1. get ecdsa curve y^2 mod p = (x^3 + 7) mod p
        var ecParameters = SECNamedCurves.getByName("secp256k1");
        // 2. get the point on the curve corresponding to mc pub key
        byte[] mcPubKey = BytesUtils.fromHexString(strMcPubKey);
        ECPoint ecpoint = ecParameters.getCurve().decodePoint(mcPubKey);
        // 3. get the uncompressed format from the point
        byte[] uncompressed = ecpoint.getEncoded(false);

        // address proposition is the keccak hash of the uncompressed pub key. We must remove the first byte 0x04 which
        // indicates the format uncompressed
        AddressProposition mcAddressProposition = new AddressProposition(
                Secp256k1.getAddress(
                        Arrays.copyOfRange(uncompressed, 1, uncompressed.length)));

        System.out.println("mcAddressProposition = " + BytesUtils.toHexString(mcAddressProposition.pubKeyBytes()));

        byte[] hashedMsg = getMcHashedMsg("0x00c8f107a09cd4f463afc2f1e6e5bf6022ad4600");
        System.out.println("hashed msg = " + BytesUtils.toHexString(hashedMsg));

        // base64 encoded signature as it is returned by rpc cmd above. Note that Base64 encoding requires 4 characters
        // for every 3 bytes encoded, then the string's length is padded up to a multiple of 4.
        // So, base64-encoding s signature, which is 65 bytes long, results in a base64-encoded string 88 characters long.
        String strMcSignature = "IOc348/la3bqCb31IQg0DEVOowg6f3cKll+SmZ6ip4KVR37qp0aG72iqZwdulDmj+1bid+IdIJJhoOm1kMqCX/s=";

        byte[] decodedMcSignature = Base64.decode(strMcSignature).get();
        // we subtract 0x04 from first byte which is a tag added by mainchain to the v value indicating uncompressed
        // format of the pub key. We are not using this info
        BigInteger v = BigInteger.valueOf(decodedMcSignature[0] - 0x4);
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(decodedMcSignature, 1, 33));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(decodedMcSignature, 33, 65));

        // get a signature data obj for the verification
        byte[] v_barr = getUnsignedByteArray(v);
        byte[] r_barr = padWithZeroBytes(getUnsignedByteArray(r), SIGNATURE_RS_SIZE);
        byte[] s_barr = padWithZeroBytes(getUnsignedByteArray(s), SIGNATURE_RS_SIZE);
        System.out.println("sig v = " + BytesUtils.toHexString(v_barr));
        System.out.println("sig r = " + BytesUtils.toHexString(r_barr));
        System.out.println("sig s = " + BytesUtils.toHexString(s_barr));
        Sign.SignatureData signatureData = new Sign.SignatureData(v_barr, r_barr, s_barr);

        // verify MC message signature
        BigInteger recPubKey = Sign.signedMessageHashToKey(hashedMsg, signatureData);
        byte[] recUncompressedPubKeyBytes = Bytes.concat(new byte[]{0x04}, Numeric.toBytesPadded(recPubKey, PUBLIC_KEY_SIZE));
        ECPoint ecpointRec = ecParameters.getCurve().decodePoint(recUncompressedPubKeyBytes);
        byte[] recCompressedPubKeyBytes = ecpointRec.getEncoded(true);


        byte[] recoveredPubKeyBytes = getAddress(Numeric.toBytesPadded(recPubKey, PUBLIC_KEY_SIZE));
        System.out.println("recAddressProposition = " + BytesUtils.toHexString(recoveredPubKeyBytes));
        assertArrayEquals(recoveredPubKeyBytes, mcAddressProposition.pubKeyBytes());

        // check we have consistent mcpubkey and taddr
        String taddr ="ztdKq1uqVEfVjhJ16kyKEANmeJes1xfmpP1";
        byte[] mcPubkeyhash = Ripemd160Sha256Hash(recCompressedPubKeyBytes);

        String computedTaddr = toHorizenPublicKeyAddress(
                mcPubkeyhash,
                new RegTestParams(null, null, null,
                        null, null, 0,
                        0, 0, null, null, null,
                        0, null, null,
                        null, null, null,
                        null, null, false, null,
                        null, 0,false, false,
                        false, 0, 2000,  false, 0, Option.empty()));


        assertEquals(taddr, computedTaddr);

        /*
        The MC signature can be verified also by a solidity smart contract:
        

                // SPDX-License-Identifier: GPL-3.0

                pragma solidity >=0.7.0 <0.9.0;
                import "remix_tests.sol"; // this import is automatically injected by Remix.
                import "hardhat/console.sol";

                contract ECRecoverTest {

                    function checkSignature () public {
                        bytes32 hash = 0xd916cf4be5a16a1cf6a375f1c54b67ccb87a6255b5a0e7cbfaef29337a3825ad;
                        bytes32 r = 0xe699c47be362524214923eaa47ec84e047dca5317cef0c33a7b13c8387498eae;
                        bytes32 s = 0x37e636b42194a665c7d9b88792de96ec70f07ccf81d6a18a84ab4377555df22b;
                        uint8 v = 0x1b;

                        address signer = ecrecover(hash, v, r, s);
                        Assert.equal(signer, address(0xf43F35c1A3E36b5Fb554f5E830179cc460c35858), "wrong signer");
                    }
                }

         */

    }

    @Test
    public void testReproduceMcSignatureFromMcPrivKey() throws SignatureException {
        byte[] hashedMsg = getMcHashedMsg("abcd");

        String scPrivKeyString = "c811a42bbcf269cb551fa517d90431cffc16703184ab1adce14c7671efc61704";
        byte[] scPrivKeyBytes = BytesUtils.fromHexString(scPrivKeyString);

        PrivateKeySecp256k1 scPrivKey = PrivateKeySecp256k1Serializer.getSerializer().parseBytes(scPrivKeyBytes);
        ECKeyPair keyPair = ECKeyPair.create(scPrivKey.privateKeyBytes());
        Sign.SignatureData signatureData = Sign.signMessage(hashedMsg, keyPair, false); // this works!

        //var signature = scPrivKey.sign(hashedMsg);
        String str_r = BytesUtils.toHexString(signatureData.getR());
        String str_s = BytesUtils.toHexString(signatureData.getS());
        String str_v = BytesUtils.toHexString(signatureData.getV());
        System.out.println("r = " + str_r);
        System.out.println("s = " + str_s);
        System.out.println("v = " + str_v);

        BigInteger pubKey = Sign.signedMessageHashToKey(hashedMsg, signatureData);
        System.out.println("pubKey = " + pubKey.toString(16));
        byte[] address = getAddress(Numeric.toBytesPadded(pubKey, PUBLIC_KEY_SIZE));
        System.out.println("address = " + BytesUtils.toHexString(address));

        /*
          $ src/zen-cli --regtest signmessage "ztTw2K532ewo9gynBJv7FFUgbD19Wpifv8G" "abcd"
          H+aZxHvjYlJCFJI+qkfshOBH3KUxfO8MM6exPIOHSY6uN+Y2tCGUpmXH2biHkt6W7HDwfM+B1qGKhKtDd1Vd8is=
         */
        String mcSignature = "H+aZxHvjYlJCFJI+qkfshOBH3KUxfO8MM6exPIOHSY6uN+Y2tCGUpmXH2biHkt6W7HDwfM+B1qGKhKtDd1Vd8is=";
        byte[] decodedMcSignature = Base64.decode(mcSignature).get();
        assertEquals(65, decodedMcSignature.length);
        System.out.println(BytesUtils.toHexString(decodedMcSignature));

        BigInteger v = BigInteger.valueOf(decodedMcSignature[0] - 4);
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(decodedMcSignature, 1, 33));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(decodedMcSignature, 33, 65));

        assertArrayEquals(getUnsignedByteArray(v), signatureData.getV());
        assertArrayEquals(getUnsignedByteArray(r), signatureData.getR());
        assertArrayEquals(getUnsignedByteArray(s), signatureData.getS());
    }

    @Test
    public void whenStringConstructorWithNamedCharset_thenOK() {
        String inputString = "ztTw2K532ewo9gynBJv7FFUgbD19Wpifv8G";
        byte[] inputBytes = inputString.getBytes(StandardCharsets.UTF_8);

        String outPutString = new String(inputBytes, StandardCharsets.UTF_8);

        assertEquals(inputString, outPutString);
    }

    @Test
    public void testCheckMultisigRedeemScriptWithCompressedPubKeys() {
        String redeedmScriptBytes1 = ("532103974d0bd1c95c5bb754e4034185041985272dab6c4a014326487280035ede404f210391154f2d09d5a6435f640d2f71a4e2bb0e47df249ac4ef59b8282898b5c7233821034cbf76a316cd68ead959ff00c4b5570085c270606b8ff65e838b6c7fc8efd65053ae");
        var res1 = McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScriptBytes1);
        int thresholdSignVal1 = (int) res1._1;
        Seq<byte[]> pk1 = res1._2;
        assertEquals(3, thresholdSignVal1);
        assertEquals(3, pk1.length());

        Iterator<byte[]> iter1 = pk1.iterator();
        while (iter1.hasNext()) {
            byte[] k = iter1.next();
            assertEquals(0x21, k.length);
        }
    }

    @Test
    public void testCheckMultisigRedeemScriptWithMixedFormatPubKeys() {
        String redeedmScriptBytes = ("5241049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232102ebcf7cee690099a35c19b7bb49f8685bd77136647632cad9a173d5199ad38ae921023d63b961ffea8dc7d5532282a9eb660ee11a9841d2a0425f09d82798e1ffba0c53ae");
        var res = McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScriptBytes);
        int thresholdSignVal = (int)res._1;
        Seq<byte[]> pk = res._2;
        assertEquals(2, thresholdSignVal);
        assertEquals(3, pk.length());

        var ttt = JavaConverters.seqAsJavaListConverter(pk).asJava();

        byte[] k1 = ttt.get(0);
        byte[] k2 = ttt.get(1);
        byte[] k3 = ttt.get(2);
        assertEquals(0x41, k1.length);
        assertEquals(0x21, k2.length);
        assertEquals(0x21, k3.length);
    }

    @Test
    public void testNegativeCheckMultisigRedeemScript() {
        // extra bytes after the last pubkey
        String redeedmScript = "5241049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232102ebcf7cee690099a35c19b7bb49f8685bd77136647632cad9a173d5199ad38ae921023d63b961ffea8dc7d5532282a9eb660ee11a9841d2a0425f09d82798e1ffba0cffff53ae";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            assertTrue(e.getMessage().contains("Invalid length of redeemScript" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // invalid threshold signature value (k > n)
        redeedmScript = "5441049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232102ebcf7cee690099a35c19b7bb49f8685bd77136647632cad9a173d5199ad38ae921023d63b961ffea8dc7d5532282a9eb660ee11a9841d2a0425f09d82798e1ffba0cffff53ae";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            assertTrue(e.getMessage().contains("Invalid value for threshold signature value" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // invalid threshold signature value (zero)
        redeedmScript = "5041049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232102ebcf7cee690099a35c19b7bb49f8685bd77136647632cad9a173d5199ad38ae921023d63b961ffea8dc7d5532282a9eb660ee11a9841d2a0425f09d82798e1ffba0c53ae";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            assertTrue(e.getMessage().contains("Invalid threshold signatures byte" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // invalid threshold signature value (INVALID op_CODE)
        redeedmScript = "0341049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232102ebcf7cee690099a35c19b7bb49f8685bd77136647632cad9a173d5199ad38ae921023d63b961ffea8dc7d5532282a9eb660ee11a9841d2a0425f09d82798e1ffba0c53ae";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            assertTrue(e.getMessage().contains("Invalid threshold signatures byte" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }
        // invalid number of pub keys (bigger than expected)
        redeedmScript = "5241049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232102ebcf7cee690099a35c19b7bb49f8685bd77136647632cad9a173d5199ad38ae921023d63b961ffea8dc7d5532282a9eb660ee11a9841d2a0425f09d82798e1ffba0c54ae";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            // the parsing is not consistent anymore, it might happen in different ways, here we read a bad size for the next not existing pub key
            assertTrue(e.getMessage().contains("Invalid" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // invalid  number of pub keys (smaller than expected)
        redeedmScript = "5241049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232102ebcf7cee690099a35c19b7bb49f8685bd77136647632cad9a173d5199ad38ae921023d63b961ffea8dc7d5532282a9eb660ee11a9841d2a0425f09d82798e1ffba0c52ae";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            assertTrue(e.getMessage().contains("Invalid length of redeemScript" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // invalid lentgth of one of the pub keys (0x22 instead of 0x21 or 0x41
        redeedmScript = "5241049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232102ebcf7cee690099a35c19b7bb49f8685bd77136647632cad9a173d5199ad38ae92202ff3d63b961ffea8dc7d5532282a9eb660ee11a9841d2a0425f09d82798e1ffba0c53ae";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            assertTrue(e.getMessage().contains("Invalid compressed/uncompressed pub key length" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // invalid null script
        redeedmScript = "5050ae";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            assertTrue(e.getMessage().contains("Number of pub keys byte" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // invalid empty script
        redeedmScript = "";
        try {
            McAddrOwnershipMsgProcessor.checkMcRedeemScriptForMultisig(redeedmScript);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
            assertTrue(e.getMessage().contains("Invalid number of bytes" ));
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        var qqq = McAddrOwnershipMsgProcessor.cachedMagicBytes();
        var www = McAddrOwnershipMsgProcessor.cachedMagicBytes();
        assertArrayEquals(qqq, www);

    }
}
