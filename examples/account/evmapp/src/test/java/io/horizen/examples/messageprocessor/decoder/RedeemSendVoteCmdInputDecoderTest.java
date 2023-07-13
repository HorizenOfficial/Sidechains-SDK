package io.horizen.examples.messageprocessor.decoder;

import io.horizen.account.sc2sc.AccountCrossChainRedeemMessage;
import io.horizen.utils.BytesUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class RedeemSendVoteCmdInputDecoderTest {
    @Test
    public void encodingAndDecodingAnAccountCrossChainRedeemMessageProducesAnEqualMessage() {
        // Arrange
        int messageType = 1;
        byte[] sender = "d504dbfde192182c68d2".getBytes(StandardCharsets.UTF_8);
        byte[] receiver = "0303908afe9d1078bdf1".getBytes(StandardCharsets.UTF_8);
        byte[] payload = "ffafe9d10303908afe9d1078bdf1f123".getBytes(StandardCharsets.UTF_8);
        byte[] receiverSidechain = BytesUtils.fromHexString("237a03386bd56e577d5b99a40e61278d35ef455bd67f6ccc2825d9c1e834ddb6");

        byte[] certificateDataHash = BytesUtils.fromHexString("8b4a3cf70f33a2b9692d1bd5c612e2903297b35289e59c9be7afa0984befd230");
        byte[] nextCertificateDataHash = BytesUtils.fromHexString("1701e3d5c949797c469644a8c7ff495ee28259c5548d7879fcc5518fe1e2163c");
        byte[] scCommitmentTreeRoot = BytesUtils.fromHexString("05a1b84478667437c79b4dcd8948c8fd6ff624b7af22f92897dce10ccfb2147d");
        byte[] nextScCommitmentTreeRoot = BytesUtils.fromHexString("3ebf08d8d1176d945209599be3b61c2e2e96d6e118baf146b77cf53e2f9a39d0");
        byte[] proof = BytesUtils.fromHexString("020144aecb153c070a586b350296349d15879e145a7e3f8749bd67810a99b425a603800002219acb27a7b866e13d1386953d536f22df70448fb4e41edf0230c163aa38862e80a123fddc02afe3d3470f1826b8035f246a87ec74136cfe7ba6945a2fe7aa4728800002ad42960a8a16e2b1858f5fa4cbb93499406de3358edf612ef7e30f69b0e92c1f80ea9f4bf4da1d71306f6818d98ae8d431d1463a63edc56c7af288418675e48b23000001196beb71c54005c237c12c583de075bdbe1de8cf46a2729d96a47c3927504d16000002453bc4504c10ce8992858873da36ddd3c684400a2272138e61d17f9109b7e70400ca4d66e572db5649af3ad26d8c0670a4e72e73c3339d2c8d2967016d513fac1f000003e6a79727ea1b9845aec048558288dcad394933f36152f1f5a3c37050270ca72700d60c2a91413890deae7407ee4ba18c905476ba51b8bdf0d9b62cf1c9d149f30c8092ab7688bbfdaa30db917a718c3bc53c50ab79192683b5acb969fcb93cde332d8000021fe8bec8e922434162027f7d69ae890f3b5a1bf9d4b6e1c707cd40c682f8be300082449ba6d075041a8db8843e45041299a42acc3b2eafb786d0cb64df71f5ff0580000607e727548381e23fbf70229fc111059abbe8ab8da35180c65512f6fa563360158076c77d78673e897ed5a62e358e8138cd6f4997afc948f27d9f64a0e8d6068437805edca760e2a29cb38f3fd19715de5e63f3cf7412279f72f005381884bdea1b33004ba26537ceb713e33f2d97b98c1abc147d4da1c71836e411542fb6c00a462d13004f461b9fa35facd789df24b5840fe5f02ef85ed0622b211f6abffd857801692000363267bdafb79271ff25c8dce6c7f5908cc1db5d2311b9deeeb09e224583eb05000084a66ed3312aadb2dd2b2f9f43c53d9f1d7cf623d02187a3f8b8210ab535150a0fb7b227016fdd0463d97cd30ff4a62e49c75913af789d20d9bc98e11ae93028b708afb8b13c272b55efd3d1eb8000576e799201ddf58373f6501c329e109c37fcfbafe6b443448793f2715a7083b627e9a26512f4db7c9425e7c5e4db5cef19068ffe4aa2d77bce9e17a12eb3022562f509e32dd74345542f66d4f72726a738222ae780c16e75b36ad4dd3b1ea0e9de8fe3ff8b14a06375544d804f2c7c0f0016f035af011a93438e0c089065a46ae405c143d14989cfe00a2f6f732c8a721a2ed86aa6378a338e4056ed917f711f22aa82351dbdc019b370c4f92bb0ad2d0b0d378b29dc5a2183b3e9ed64a0075ff91dfe00d3ca34d6f55d7b5cab7b58c71b27bc8feac0333ddffead3f94760168c165fb3a8fc1789b34ca699a6e14604202d2c1e97c6352f109c6e7a81ef7f1381348846b1dae45e688d706ccfc78a1e82abec68dfdee963d730d51afe856f158b6ddd940704d1b85c6ecb85134e590ec2fe75133e5b44b2dddb340c72dde5e06692ab2811b595bb54623d0ef5f5acaa03e5e9416ae2afdef01bce221a46b202e14016469cabd58c7cb6a22af2925ddd2066781b9fcc47bf33028d18a37f0a2b6872e0277f7d3c1a4db42a51531811e100e4c99d39a243aa1aa64da2467ec865f7a0366dfad9c50314b60e3fdda4178dc15873b6b0bf8ed75075f3a1e69aee781a9e5b2847c791d31a09ddd1c31e2172615e4937d7d83a51f8f3f30598aee279b29cee7f7b349b4efe9582e5020ebf09107acfa54839a4426049196eb579fdd2810ccdf59ed5882033055acda27c97b932e2c9bd63cd2e9470739c20c0b373cc6400ffd580655ac91f90a6acc04f180bd07e0bcfbec15d7a3e44e759696d61c256ccecff0464f69d510d0f56a1c9b41a127537f8a2457db38ca0a7d3626ae68b0daed66f3925a1089edb6c2b6214ee25b140fa0471e3335e65b95735392f7730bf5cbf69f771368fcd472f88f2c4ea5c7370b80ff1c45065bee341f450fba0ff489d445473da9433607bd597eea2113fc1f501b00c25dbc3fe054517d44ad38c7537bd056a1d68623324642fd135de40637fc053180ac0a87485f012e25bf9070b3735cc75d4e84f2725c21b278270c80e20fa03e0d00d68cc51f563affa5c71e5cea15d914e0da97ad884cf1780949a88da272134508806c01a11876d58423502c463a76b70d5b9bb859bc7dd5d32d0fa5bfe89a69f83800aab52fcec4964967e9aeeacf4c27867a53500c87f9218fe0fc4ed3b74107db2b80922f7038f89e6274b281a2328b5696fe10da7ba94ce7c8ad3282757a12ec7a26003c502b5d32e5870cfb9f2c28c6d26db957e20aac9b3918c4750c15a2f779fe3c004c26ef7ee3101b23a6741964dd9d38bcd6edf8cc9876ce4194462deed83335098044e1b90b1a729a658629f72d099af5465fa9453dbb7435a24f3918c1a0fa7e2280fa105c46caac8560a6f01d2e4fe7bbc1a5f9f9d3fdd6abd047f3dbac471f89190064750f2569e9449d4617a48be45518b9b5d3f02cf01fd36974f07eea1f993c01801af773b9a2654d96471f37dfd9ce8a607aeca3641a2f6f4c1234f263b93a912a009720d2a5a1878605c72289ac57f8f0d474f50b5f0e45bde531a3df5606a0690880d2753de632ffb47e80fefddfc061363bfdd1291638a5ae337c5bd9364a631926001f41a0293287809e98939f2561173b782865f084c5a12d042b553809cc515a328013c08166836c598412a6a355339b2290d19e4aa941c4c710919717bd15a7070b801a3ff63a6f8c438fcd4e89d22f20edf451170dcf1bbaee219caf5e10caa688038080c03a80cf0f2117ac989ef0f7980e6776b7e0727129cf4d443951053d835d1780f48f7d69ef3c3489e3030a96e5d2c61a766f87508d621c9bfc082cd7c1bf6d2a00f7639cd280d7589ca2d9591329a22002d784c98bbe0ebf5844f57a9f8f6157118002e3b05b2f6ebf33656d98a8ebaebc9e3daf2bf0e1363b7813e7fc660ca6df3e8037419a4635859c8970f1e129faabc761bc16d1c52bc6739060ff9280aef7f51580a48f81d3b5ddd8dc790df9edea56176f1bc73f7d303c14c1a453bb00edaa572e80c0b3f834785cd44d492b4cde362b4b53f77d8cd4e3991519f8e079e8d333d91a0027ae3df6575d25d3a0b7411ec6e9393c712e12e37aad519276cee4f7b4cdae1b8098955a58b72055fa6cb7df0960150926ce4a1b9cb0e939f9ed7d9c45ee6a112d00c2ce1f5390e0dc778a5eedcc64d62ec652620d06e8e6977cabd1ff6b6a57f7080060178373f683c0a3505480b74dddf0d38c2d70bd9aa0355b2ba67b74e4815c2780ad37702345ff2ede320ef28eded48ef3bd717a0d06505879922b437ef22c042a001affcd713d825ece06b288dc3732c70ed1ce2b46ba774830c24f205c57c8f00001717f8e57134e51db2be92ec4bc41a7076ad055720e0567ea6de490be829ad3080001b7e64ae3723aa1c92d34539af02ae1b3a55b57a84eb3c383151da1d618ee060b0673462a7391c9f4290f5f2280aa8454944143789a085a3989adfc05b158b8ab1e80580c03541eb60f2f7ff5d93a719d0e099452714292cb74bb1a57d37eb601d908008adb5806b900004e5f8241cb42ba751f6be7f28395cefd853b6103ce4111952c00cbd81d71e9a3704396288469a3417462d351ae7125bac47190e8ea2eb7144e25805f173704ec851a11b69479cf49e5cc5d0ed6739c8ce8c427f9d1310f78e8852000dd0159495c3303a3553a8ec879e92755b25df520de84522775380284d5ac130900");
        AccountCrossChainRedeemMessage accCcRedeemMsg = new AccountCrossChainRedeemMessage(
                messageType, sender, receiverSidechain, receiver, payload, certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof
        );
        RedeemSendVoteCmdInputDecoder decoder = new RedeemSendVoteCmdInputDecoder();

        // Act
        byte[] encoded = accCcRedeemMsg.encode();
        try {
            AccountCrossChainRedeemMessage decoded = decoder.decode(encoded);

            // Assert
            assertEquals(accCcRedeemMsg.messageType(), decoded.messageType());
            assertArrayEquals(accCcRedeemMsg.sender(), decoded.sender());
            assertArrayEquals(accCcRedeemMsg.receiver(), decoded.receiver());
            assertArrayEquals(accCcRedeemMsg.payload(), decoded.payload());
            assertArrayEquals(accCcRedeemMsg.receiverSidechain(), decoded.receiverSidechain());
            assertArrayEquals(accCcRedeemMsg.certificateDataHash(), decoded.certificateDataHash());
            assertArrayEquals(accCcRedeemMsg.nextCertificateDataHash(), decoded.nextCertificateDataHash());
            assertArrayEquals(accCcRedeemMsg.scCommitmentTreeRoot(), decoded.scCommitmentTreeRoot());
            assertArrayEquals(accCcRedeemMsg.nextScCommitmentTreeRoot(), decoded.nextScCommitmentTreeRoot());
            assertArrayEquals(accCcRedeemMsg.proof(), decoded.proof());
        } catch (Exception e) {
            fail("Message should be decoded correctly");
        }
    }
}