package io.horizen.datagenerator

import io.horizen.block._
import io.horizen.params.{NetworkParams, RegTestParams}
import io.horizen.utils.{BytesUtils, TestSidechainsVersionsManager}

import java.time.Instant
import io.horizen.fixtures.{CompanionsFixture, ForgerBoxFixture, MerkleTreeFixture}
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.consensus._
import io.horizen.secret.VrfKeyGenerator
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.box.ForgerBox
import org.junit.Test
import sparkz.core.block.Block
import sparkz.util.{ModifierId, bytesToId}

import java.nio.charset.StandardCharsets


class sc_node_holder_fixter_settings extends CompanionsFixture {
  private val seed = 49850L

  @Test
  def generate_scGenesisBlockHex(): Unit = {
    val parentId: ModifierId = bytesToId(new Array[Byte](32))
    //val timestamp = 1574077098L
    val timestamp = Instant.now.getEpochSecond

    // Genesis MC block hex created in regtest by STF test on 09.02.2022
    // See: initialize_new_sidechain_in_mainchain method and test node config file.
    // related data: see examples/utxo/simpleapp/src/main/resources/sc_settings.conf
    val mcBlockHex: String = "030000001ee694a278fa73591e40b646cc4028f62f3d8c7948ff204787960ab6403c4a0c258529faeb441e9163815bac1ae80a97b908d5ca6e459721f3e62a5364cf83fb30619e7f068dad90830496338b9824d6f5df9e707cb1e5b5f5d59078b3a7a5298cd40362f70e0f202c00005abc7a90c410f733f7704647bd0ff1f5ae39d27004f3fe6b2b6e4a0000241a33713b030af4cf1f2da118b1641401af7422166515838d14fbb73fa6d4f9393f4aa9770201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502a4010101ffffffff047955b42c000000001976a91439f7c7b97c8282bda0e8cd44dfb77787bc405a5388ac80b2e60e0000000017a914ea81ee2d877a25c7530a33fcf5a65c72f681250f87405973070000000017a914e7d25d82be231cf77ab8aecb80b6066923819ffc87405973070000000017a914ca76beb25c5f1c29c305a2b3e71a2de5fe1d2eed8700000000fcffffff519d5e7614a82e69a1e76973bce2f9ffd597725e541173888d96efe73eb78ac44f000000006b483045022100d8c593837e27b58ad051fb8fe2294efa631a9488301d2b4887668fa5351b304202200a62c5a9327bc959a125af74f0e5aa1a627736767f008d1ac7e7443090dfc875012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff967423b5c3d521139621f887c6ba20e3a0bffb6aa6531cb59c077c66e3e501d9000000006a473044022050a19d67cfc6b9a9ec32eb8b02a1cf6a6de3803ee08ea4421799873ca2e75b7702203de6ce42dc3e9a16538cf0396e9934988efb5579224a3b80b8f07c4e08dd7f32012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff96936988aba1e9a4a68ea239ad62620e6e33f5ed355a270fda92eebdbe1d98fc000000006b48304502210098ec9a352966c6bb051a4415dae27bb2a1cbeba9553214bf36b3bf437598840002203125d7c45e0ecab3a06648f5843130237993fc5c16bc0019b9e5cfe48c59dcd5012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff42ef8e72e5355ef3c5a976d0a4a6c4600e53faf93067c60e477fa3c2fdfd4da4000000006b483045022100eaf3d6624aa87229b16251f7bb5a5998a1568460658a946eefcc4318c04804e30220441967df76d8154e453fccaf1cd6f7f155c53822daec17cd461965e9c38cfd52012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffc61e6fdb6a3c8d8291c7984fc353779fd295d809aa0e2077f79392252387f45a000000006b483045022100aef27aada8f103e214f4324f895e4477564dfad128d8e916ae03cdd055fd22c00220557034d3e33c988155ebc0e818db269f4a71a0a084c910a3c403c43447408245012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff65670c59a438bd883e2734a94045632feb5c5aa19c133ecf321476540e79352a000000006b483045022100c0aeac4d46577f5cf6cd8bb46759519da8aa34c0eeab6b5558d9d4c903d9ddbe022008bab448d21f72dca0ca501c791d959b1df7174e757158baf4dd0c7878073b98012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff3f9bc413d2ba22708124e92443ad08dd0e4d7708b6e223b091a85754e456a18a000000006b483045022100c8b272acedc2f116b77938461c78f74352edb5b7a799c8a364fc75d614e3447a022063582bd84559f31479f47cafc68073ccd207dc6117bf76366d616b835ae9fcc7012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff98587fcd0e1b3d3c9e5229ee51f7130ddbd48350db740690c69dbf6a306276e9000000006a47304402205ff287f116678d924991718dda1cfd0dc65044494b7b1a8a79d003cd4256c7d102202a8b362efeaf030aa48906790f2e56ae49ed80ef4fd783a64f99c6eb0e0d5edc012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff3df206e10bf694b2c8408e0a6eeb8c5896641266f66e6495672812b554fb5ce6000000006b483045022100a62016a68c82a7333c44e43cf159cf966cc38e304c48b10f341474eb52e54fb802203d17bfb0fdf8cc5a6724c5eff36cd3c30a5cfa21646b2022eb62cb9dff62f520012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff3d4a97aaf8f6d9df42fefe9fa529903592a3eb62f3e59df02ffdf756c092957a000000006a4730440220092b891542b70ebb29b9050407350d57ac3e4e2692786bfec4291d1e01cbd9650220228084c477e73fd48d2ff3060db6b7d5b3e0b102349826b6a05335b79172177a012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff9a49c9adb249b5d4226b02a797143f9a553f7a0b40448356e913e37a0d47796e000000006b4830450221008da820018174c893b3b2adf2413efa55db69aa83b7cfe0840f2c4734aaacdc8302200a3da54d964c65a17336caed74d0799e6318b96ca7a99baca6ffdefc591e22a5012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffc5c4380a7c84ba87cffc96c2685a5fa189f85354dcbc449516a755cc12be7055000000006a473044022062cf0cfa9400a9581509054b170379610cf2d5a861a352549dff059704dac6ea02200ca817e934f5b2ad6305bcfa9dfd692561d2a32b67609672b8dc96e612435c43012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff9c26a62fb4f0fcaaf30f5e432d03e3d63ac757281369257a530f59c92f413bbe000000006b483045022100b4eaab12f0b490eaebd446208002f768c195b2d6481b1d5bc57b866eedcd468e02203e810baaf4a4a8a999fd09ef252f4f531e500015de8f40538972e85ba8f75a4e012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff39f27a46db2deec64a5111d7c0f6b69fe8914794935457de96447611212817e1000000006b483045022100d7b94b4487f7aeba71f1e8d916bcadbfe32f1f3a29cdd98734ac3dc1fcd237cb02206b277bbd11b9228d227dbd8cd4f91cc2ab5e1a4070cc11b19330ce12fa1c500c012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff39e90fb610fd6eccbb259716aab87072adc54eb75abb64ec19ee22a02648407c000000006a47304402200b3528a2b53563907c1d69e3c735c4a9388dfef8145855f49b2423baefc927cd0220482bab52b032aa2fb565304836eee5f92282691d0f8a60df407b652aafafad5c012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff9c98f9e85fd2567dfe4ea9c04a25aede889dac6c9251447faf796f171da0601d000000006b483045022100e72600096257ca22c034155aec5cbd8cfbc8ee0b6484d13e3f021a77c2b5ed0302200829c7e2d40c496cf1e599a724a78529cc1c617c03e0e4dbf4347e97e65548bd012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff44b20f5245c7fb99d03052d69506efee42f7d09d9b5a95ed165197fb93328ea2000000006a4730440220567987c9a517c4d6182f650904c53f101a8d1a62a954d69a63d373ed41ae405002202c86d74c0cf2ebaa3739a49c35ea11fe6e67581b3ece07018eacb145f7b0f544012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff687b7ce061873882255d3cde953422f6c3fd550356dd81874eaa374377563960000000006a47304402203b95301092e45ec8ea409d87f16ae9fa5a2840021c08298b681f8d47b0b8f32b02203ed6f440ec0018b66995ae2a16fff67d19b6a49cb83404208a8879fe4be12823012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff69e8c574279237bb1eb8f0d0246b535e9f0da5f1bd74b57fe52fa40988e3a3a6000000006b48304502210097612b4e987f52c9c3c5c1813d1bb0c3a5fb14499654dd003917f16b7cf79c86022015152a880bf6dccd0466a7424135c80c8a8d08032fc71f14030eaae60f87fd09012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff9f608733e28ae01b78ff70fcf590b972f442feeec449fb2041c76e9202800bcb000000006a47304402200989d42ad8903a231a0281b6bdab47eb2453787805b9f7bb548e756355efb77a02205a5eaefe8146f655980e7f38e3af250a7ca070c36eaf42de9a3ad499e2c67eac012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff9f694f481d636404d9f8b0e13c7b259801782d9e5a28671d80a64459c75ed17b000000006b483045022100a676ea270d1cdc26e5db1ebf55e0e7b8b0415522ab0cf3c82b356208ff3d16ef022028e105bda282401e77bf5d982ae87e803fb2aed76fa74f006fc5979e2b1dfb86012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff339b6596c3f8bc1820ee5d4b30cc08d363b2e09ce7d9e5e5655f9e093c6b0475000000006a473044022079f65be7f2874bb5d6d59a0e87a4765d7e9844f2c58c32a05ddfbdcfc70e9b6e0220482986015ff18ddf7a5c989602d094bbeb00770431fc966ebb1bc5576274cd5d012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffd07fb2208772bd3cafc8e31daca186e530cebe64a32a26748dc87ca330680c36000000006a47304402203b39d7888fb9b464db0f66fecda3ada3a133f0809e88520313a26192a221bfd3022039b06533709311e08f5c7299c1c73443793fc585e19723b19893635518a74012012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff9fd2ae290630ed6960cb6c63c9ce9b44c49db190bf6ecfd84e375931499e90f1000000006b483045022100f3fb9fc144d2671e116e877ac7a0c7d5cb7d081e0c6a7deac21b5d610d906d4c02205f146ac69633633e760442fc53c57f3db7b4c20f0634d03478ecf19f9c2444a1012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff32b737ebb835811748addde4c50f610354e6753e33337ce44cf101ddc77d74ec000000006a4730440220047b295015a1dcfb6a8054ff3b52078b9192333406d40fe1447089648942b873022043fd4177f43b50e8ca857914a0fdc5ee1b2c250adb8824909046e0b7e778f7bb012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffc30e4ede2fcb326caf6c05792d6413a979e91de38fb8467986522d31524ba453000000006a4730440220218e6a21a3497c3fe060be37b3ddd14c059664db71d3e3ccef296e27996baf47022061c3de45fa4b11eea0293e115162fff627c1225bf95257940676b33cf9fb08cd012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff2feb33bf1e64ea528306d1fbcf9156e8eaeab4a53375e359b27f8eee39e519fc000000006a47304402207472abcd40200ea68dff5f5537c9a7f751aa50472f5dccc10a739eca779000b202203d8d0f3582901e77790d16d4bc9684267fc7438f0f9b96a08508ac88c305f7fb012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffed14e0ff6bba492b6f00e029b9aedfb3fb8b4b6c968062127cdd8ad17f129d15000000006b4830450221009e65f7bf050553fe548398021cd6507e336f3524091279f533f07284b264c03902201f1e506ebd6d57eb25101324b08811698b770b362e40efb3b680d6e0c3ced26d012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffa081089541d9fb17d741a183d65cc33d8f136ef5fd6fd1ec0a874753460b3e18000000006a473044022058890b33ec66d3f374dbe731abea0c8d3770e5cf07bbd323aaf21c54cac9d69a02205ed8248ade37f3217d0a0bf1ee4a938d7084f808981663282a7a1259dc3e1d39012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff2edd5c542440cb3ccc934fcd560a16298d6caab5b4136354cbe94d14193bc4de000000006b4830450221009e2dfef5c0605824756c74886a79039182227fb7b069282903b2b9fdf3a664e502203d1cb2cc9a73b20f6a13776b554f3214d96ff280f6f143cd67594ed300fea4ef012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffef08297cb2aefad29af68504e2b68f8a1976976bf293ace1a19005cb5c44a007000000006b483045022100e9024ae4de264fcf2d92c9425c8651e868afc252b7e993565c851a7a98f15d0d022013cfc8785d136039f14234e7c4d7f9b4fd17f2e823768dec0a0a9e2700fb55d1012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff2ca85ae2633f092ebc9312e6497035916a2118f39241a5e97fbf97782d579142000000006a47304402200d99f843f026554178cf622441088dcb569f1e152ba94614783668f6e543fb89022058e44a1acf5634b0c57cd901bb908a5b6a3176272af713b7af80675668bf25de012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff91e1d25e1966810ce69f0fc9292d1ec14ab4b25ec08cd570e7ac8d84a714f37a000000006b483045022100ca9db8d70a5f3d5aca540d7e52ea522bcac70e34d6ee7f67861e0ac64d1cd477022061baf04382e359b3f8fc9839107b4ed09c1bdf751bf683eca8afd0bebb585561012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffd200704aeca77d06d9e84427c03935b496508cb818673c142cdd711fcfd6abde000000006a473044022020c867c09ec77ac6a79e5d5fb7c836692a544a82e51c2ca0d54151ce70da041002200acf6b0626dde99af1d2980a538c73f8913f6bbd04521e768d4ac19f96428166012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff5cf8dec6ad43859555fa38a891753e3b54a2df22345be98a77780089f66c015b000000006a4730440220575cd6944380765fad843b021f26327805bf3ad105ca7373f889af74889acd680220062ff23a838669db7d15648293e236b8e05272bb528fd4e9e95112067200f066012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff89560aeaac358ff28c4d90b048ecf5ad595a167ccfeb7958d00fbdf16def0d98000000006a4730440220692df311ffffad8e94dbb615a555965720605b9447eaef3783f8456b258b705d022063712699470316cb2eb9b004e48098f58eeb401e5f04b84acd1aaa71ba6aadca012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff5c2931a3218b3caf9f7be893217c1d1ddc835e0eb77f25b3b9ec9f4b6f2684fe000000006a4730440220606fd5a5e0c1ec6764b70a0efc08dd37cf059662030b2c3ed55c082ee988b1a4022021f5f2054d068014b3ab0e561e9aa7fc0ac60252ce0f82fe1ff22d87d885ed77012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff8d06a0190a2d4c80787f0374d3f4f3abc169d8d9dfe01f0bd0d13c557559914b000000006b483045022100c6b1a974dd1a5ba27c133d455d6835a5836d69c9a560edf3f29c5df68765fd7602203417b2be5d13394fd1abd7844bc97997fe22a7b72884d862a444ecd6f7f18905012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff5a8a20cba11a0a123d64596919fc3e0897f3f19ccd802f8428ccca893a38e6e9000000006a473044022005c1044b313090c0a0e413a4c855981b17996724b02362146bf00516047db6550220639991829cc7dc62cc5262a9d3e82ddd87610d22604c43fde9d5c524b4cff86b012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff874cb30741e0a8c7db94bd8e21a661f4fb62afa308fad1162fa082c086051037000000006a473044022077e72bbdfa2c52c83a50a1f8a49c867f841990a52a8cdc341a703675edd7df8802205a8616dab206404dbebd16f94014e22d8a681ef1987c2720da5020c6284bd0b3012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff591d521eb48429a7c21b7ac3c67a6872abfebee98cc9e2417cef60cb7a32360c000000006b483045022100a7d334d4c6adf16810a70b3c1a61a7f4537be61ef2d90474effe13497a203a9002204701f0d8f69242a8aa200ea719c420ec485adf315163602109793a634df13312012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffd17765cc6e104f3aa7a468c8d56fd2320d2c86fdc39d1ad77406b7c466bf0a64000000006b483045022100b863edc8fd87811c11b235880ffaba74065d5102e80e6d910f733d463410874b02203818a23d1543449a2b61bf7cdb307dcaf21cb76ad49c0f0a6f9af1c96c388ef6012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff568c6347f5c67140bea046b04a877819f27e0b1df5bf1798336990c295e5d6f7000000006b483045022100ca5467074eca2adaa3fad9b264950b72717d8c4299280b80a8286180efddc26502203c7ab56d69a4fa1ad79d5eb47590b1e700a85940324ddcb7f184deafb20bfd45012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff563f3edf0314668869f7798ef6faa5c7553539e229720aed646379745f32a5b1000000006a473044022010b9aab758adc2e8ee449dd81404e99c9a5f8510b1b39480c486d63c253b61c9022024a45af49e9f8cf9344d50fe821d5854a17ebdb840e66aeaf6f49c300beb8fa8012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff869be7c572f5f3c98483111fea11da8960e234bd073b265ec7365224db71475b000000006b483045022100c3812da2b0aad4ae67be9e414fe5c9ed5930bfd3c91b3a79e40eb2ea46df57070220306dbad527b69eba810011a219f3b51fc358aaa81c69d44c4f48a32903384d8f012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff555e470eea9af877b1891113801cde1e6f76bb047d9a636ed520850ba42f1241000000006a4730440220159980bb887bda919698147eb146384af5c22ed533d4a133b41a645c3d8ffa9e0220161830b242ba185007b9374b5fa007aafcdd7082794951ff9c164c95d0ed43be012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff5554e98a440bbd7d80ba07e2c9bc54ef87c7f89e50deebd35251be83e82d18b2000000006a47304402204166f7293a318ec2417f9ab4cbb050f9a4d9375de540435bdda59a69f32268340220253e508666f4b27280addfc419c066f2a5fe9c0e4c676f658a0a44a8cb8f2000012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffd4a16d8bbb139e52d673d92243d891c1f41d04c577461e8b05b0a85cf62a6c58000000006b483045022100acdce73ad87ee0ee7b8f47ebf548385b081a41e78ef6a10345c6f0a612991a3902202a894a87293425613a211f18151a1971ad6cd069fafc1e07ce54f0d63d346f0e012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffa0a1258ece3e1842ff6c9daa23f5ea5eaa67d183096cc85dc623e630ee82d853000000006a473044022011c3c534aad184836204d9a4c7cde785618252d7bf3fcea93f4f7357beadb2ef02205db92c141af84ea7bfbc66691d8aadaa8f9f5364314b0966e566546be89e9b1d012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff5154ca19d683af0db3ecabf19d4fcbcf9d8ac194c42ce402ae9d5ce3fb3c941f000000006b483045022100eb7f9af26a2206df01f1e785c993819eedfb035ff75724fe49c8ee9e69b877440220771f418f709d3f3d8156b003ad65abbf3d7feb46f0ba82e2e90cdb76c6b264ac012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffd60436391b985421ce1bdc459c73955cb4fdeff394fc246e66eb1304ce6a7fd2000000006b483045022100dfcb7f763047bbb67bee76822403f19cbfd85948d38f08bc0e82224069557025022076772b63b68b67707e839c6ccb825025226fa1947cd7580487190bda00a985b7012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff62c534f816fceea916990557808de249dbfef717d8707df8e269baa2905795ae000000006a473044022066912c4586d1697957b3432cdc7baba1856b0076218d3525d6973126975dcb0e0220172d94694b3dc34023bb4b3def6b143d8ddf1776b00a47dbf308c759fd2fa15a012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff9440771c84f3d30552cf2f835991a96f70c413ade48ecad603e5c9b5b48900a7000000006b4830450221009fe2cee416acf1ca55d97468a2d17494fe60c9bb96364484c36f563c0fb610d802203a07d5d1117a957ef7ec90d895bd91fe667fc1b2fa82abc7a705c51296ddb0d4012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff4c1069b0f87dcefa436fb312dfec454d8ce7b4117a09042234e1315da192bcb2000000006b483045022100a0e0123b332f2548c3007979d29792ff47ffa1be3a35622068b30c6afbde05d40220215b1be089cd5fa25d41e87c64aa6aa5803bff4aa3baccd77696e1b460f97c01012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff4a9c9eb790ed8a837f18ea3e60659b0df9a157a7c52f4414c35df67beff66876000000006a47304402201d231725752ca95037e9af171c9d7594cf2efecf0ef7b452760054888d35da4f0220379659852bac00e4f98d0d918fdcd77e2bdc7d8633c0d2d44044b8de977f866b012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff64e48a68767e344d828bd5afabb590e8956f63748578de4f9cdc78940e8b0316000000006b483045022100d0f287b3f08d24c75cf5dacc50be31bbd0fb53504c1e8a05951af04e556fcb5d022008ceb451ed2749a18ecc3c064a08c901b1355ad760dbd3f61e5f63c0714157b6012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff80329a0047c1a7020b1179d3a9d439cdb1b543a11c6bbdb4026aab0abce1898b000000006a47304402206b68f5b1cbe052d02be72287980d932c1445a3d2c13ce606ec24cabfdd344c28022026f18441befc92797a920b7d7dcd77ffa0c0bf2cbef9c4cc56f2294e900a38c3012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffc75b4bbac9e8acb610c9e332e415b5b137a9fa2dc85d28e02a0b9f6a6276fe42000000006b4830450221009e85ce09dc14144f2ecf19767399eeeab0a2f8a38ff836529b511455122e2b77022077dc6093a9604204ddb510da24110a3157eb226ac4c6400a70982f8813ba1bf7012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffda9985bd57b6f07455d6728cc76fe0509b16d7d587963e02c6c3d32475326c6d000000006b483045022100cef8f3e1b936414c5dbe8e22751b3b1f828ae3200fe921a6580d91156a70c7f202201b6ec35edb5add3f6713e23b579fb68b42a0758e9fc0628af4dd51b80a560f7b012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffdb54f50be22b704fd487fed9128e7dd84845374c9aef9e4748207c969b79f8d5000000006b483045022100fb0d4ea4b8e1a29ec79d8b475f52d2229d100b5daba9cb745ee6dd43f54bfd1b022076f2982256268913d30ece7989313d9530da8c91d17027c63a2cf77f2b629c0a012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffc71b3fea247c3b3e2030d98ec6450e2892c9af60f060f33c726c780b8c1ce558000000006b483045022100f0c6dc790c930b4b7ba3ebf2943993080b802e66b84ab27653977aca8fcfad340220481a0fd447dca2fec7e94a4236d7a9cd0bf4daf416081259a38db58d3e80f4ff012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff45903dfdb07af3384c142d81d0c7f1d8bc831b2136b2f58fdd7941be69693160000000006b4830450221009c52c3177a480701e9c29d48f50b315508633fa0a1e1f5ab36301c1d31b6a3e4022041945167428652fcdc52d67c4bcbc9a54559eedec6292dff8cafae4b2eea4b57012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff4516d35c37fc93871a0831d49c13f6cf29ad9b4ae03e678f651e9631d530d5f3000000006a473044022051966be27d1dd997bb7829cc0d66c0ca09ea659c6bd68b0b19cfa9756185e6f402201e2826de26cc202195ca616d38e1957126255f1a93430f0e4d42c611fe6054ed012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff0da1404b4216e39ba70bab09258f1a96decd33bb81f381e41e9cb8dad67d12c2000000006a47304402205ceba6f84f674e1a9804bf6e907a02e2e129462863c843a8e6820ec9c6cba47d02205e667d8f4d984d6957471b5165d543f7f6661b9942b3b1f6d77b6548abfcdfe4012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffa35edb18a4883d69d9fb1512a8a8d58dbe8e81fd80031010a3a9fe7744ae1814000000006a4730440220316c45606dac57541864bcd1380dabeea4de832ff9cf37a285b13609101af46d0220470fd967138657ac55475c8ca56fa9791f77fa74bec5e215462e38496355c23c012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff7454a108d57b3c65cfbd3df88ce02beb88623d4a4a6fe41af99af11fbf60d00e000000006a47304402204c23a1d335954d52cd878c486988efc3d2881f42c1d37bbc71784cb4742c71aa022018cb13d9d0392cef5b82f96d01f000c286209248ac0fce79bebbb4a89aa403da012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffaff70651c8ee9e600a02d560871c632088ce858f4c6ee85843ed5057d392d7cd000000006a47304402206363614ef2901da8a762bce29ce60f84dd1fc258bb269e99cbb969d33bc044d302207d2f37ad335d9988254c1461434b5a62bb0e07b7975804c7d865af4bbc7570d1012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff14c9c7b8b26724a56944b97d40971beee0fb4cb3016b2f995ebb922e8130171c000000006b483045022100a37558403d2750ad51f1498d1e0142ba79d7b53a4b42fc439ea04dad4a821c23022073c6a411bdc9aa16e6a37d0c09ffc040479fbf4bfbcc4dd8568ad47450a3086a012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff79f74e9a3eb90afe23200f32997b1ac6dcd3333c4468ba7956326a9917d7d9ce000000006a47304402207057346170cd5982c931f33554d894034542fd5695bfc2bb933acaf6c95897bb02207b618ba3f3b3ee5d40fb401184c90be5c17b90197b9a4865a1ce41d2c7600bf8012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffb1f55008455e75683df11c536bc5fa56bd1c2af8d9380db5ebcf2be7133bae96000000006b4830450221008ec4b97381595d48a632086d2903f4754ba449475d21355aad4c192e8688069b02205880ea705330d37587f81c15f30060d0420a9af2f722231a7944d24e89aa90bf012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff12afc8cf33f662330c6ee070861476f8d47057692398f4ddae80e5815e004392000000006a47304402201bb720b2f3c4de99060ff861bdb1758e664e5a20ba07334925402f6d8929159e022028d180d5bc54a12449fc0fe0ace7c8d93efe233dcdd54d2eb6d623da9cce952a012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2fffffffff7b04ec7777956dd0122e951855d7b2f8e78769e1ea0207220c03c0f12100408000000006b483045022100c366f2f1088453eb55832e0a70492c81786e27b57711d33cf5e3a7883be97119022032b2c382aae5b6e30e840d3e4789494470febfa428ac6d0e163a0eae7a209411012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffcc6e2265774b86f58f7d6633225f1a4f90d25df410fe948038ebfa5b45a90d51000000006a4730440220220d2d1ed8ff63ef905c1d035798f5ae2fac1a4e0ad4214f37831cb49fc45cea02201ac301dc37145dfe12bf16b9c08f6ddd7e07eb25548aa4d29f2aab3029659277012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffbd8c6d84a4c699df54855b372b5228600d186945f8395b896394f48ae3f571b8000000006a47304402202ad8ea3d6b5984e0296dedfc350467fc64645f2f19c31e8eb488d90d463b23230220578f38b42d4200538ae2d0862b46c094cb6e4a00c2d8b31c8c5da4739abf9742012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff0fc42d9b627a27e8309e9a43dfecc56abd2538182e41ebc1e0586948fa34578e000000006b483045022100f620d524ceafeff05f539fd9692462d4329ec75daa6cf78f27bc803ffe84b1af02203dab452c76d0727453d49866a5ca646f06cdfff1e3d1695657c7054870b36816012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffb52fe48aa9b3f42679894656363157f802d90010078951e17df0e2bd4c99d497000000006a4730440220425237921a431153f399562a91b278b9857f8c33b322860ce6115c8d0757bc4a02203f1a9ccc39d057d6fdcce233f638a6295e9b0b68a1936d5603d292019af14048012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff0ee7f310d00091725f50cf2bc11c1286e8fa30593425c20986141a4cbf5aa8e7000000006b483045022100aa614b17fe5905854d586acaddd09cc6c59b6b99a4920699eafff80a44cbfd12022040f44a9bc8a957f19254ae6abb60eb5eb0ddddd7aab38e5d22bc12647ab44d50012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffffca0539c3b26afe3ed5f1c095056a1d92f43d35e33abb5b2c6b6637f0701b800d000000006b483045022100c97d3ccb7f9f863152762401f24fc6372a84ffc802249f075d2494a73651567902201d18febe8eb9e322985b6261104f6a09b27fcdc7c945dbb07b671fb9942d9d9f012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff0ddc40b0ee529ff69fef2ec86f4f2eb9e6ecad6b78060ece75f36993124c1ca9000000006a47304402202caf462e80bfe1b92831e1f666855af72dd9c7ac4722b09c6751f0e619b8ddfc0220745af5953ac492557160e54c6cce5fbe972e10cd0112dfd55b5a8b2409fdfc96012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff15eef5e5046c082ee84dfdac9fd897059751d2607cf2ad514dccd3c363129d41000000006a47304402206ea92b03f4420d7ebe70cac8ed1b6916cae2da0f48773a562d5df5abe3b63f33022019a9ee593bd78201c930729283d8fd368206d23a92578eff2264b49dc13f4fa0012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff0cd2277816279c142416428fd113b8a672ab0afe9990d692f44bac73e1f6f926000000006a47304402202f3490908e9afb0008d519ac6aeb5058b8e5037d1562ac91eda78f5e1fe72e6c02205516214e00505fc5a851eb66ff4b79f634276bfe396d7ea5341e00287908e74d012103d475f097e833fbba94b5349fd8799dc4319183fdaa8876bc0cdafbb353b9e2f2ffffffff0187d9b32c000000003d76a91439f7c7b97c8282bda0e8cd44dfb77787bc405a5388ac207b6319ca581a5c328c9c4af2b7bda190d5925f79afd8b94921f8a81bb04242090177b4000184030000005847f80d000000acb24b2f08e8e56fe1784f1600879c697c7cd90846e076724b090fd72206b1a521076a9191a89fee51439600b0455db357a9899694d1cdad6a3c71bf65e6cce5328001205dd2244a038233aae4c18ceb65ffb99caa0cb6b24714e7916403ba3a77236618fdcd0102386700000000000004000000000000003c67000000000000bfbc0100000000000c00000000000000016234e8c194d555b5ff6a082286b8241f7a1bc3d4dd71e8a88e6db8729a3de324000001c8bbcabb872abdd97870980b4020f7d6a69bd8650298c0e8984299f6ddc9eb35800001e0b56d7145dd7b006e0d9b2ea1b5bf65c26dafc33dd36fc7675eeb58ba247613800001101c1881ee4e14ce72f762869875980e3e338ce353efd0469d2d74b6254fcc0c0000015e77dbb1889b9d36cb1f345b4290894b5d27c4fea13c19ff202cbffc54d78123800001cadc641bb884c20af1d99f02c6fdb3bc837e8a436a7710252165ec6880a8d23f8000013b43af52de1e9c5e4eb52414ff12c7ec15bc499b83395dea1351640e778bf228000001c3913d5f157b058abe12eda99febaa5bf8cd3eecd768497a12112f22dc3cdd3e000001bb2e8f15c078157fbdd179843702ff55e432cac0fa60ba7459ed918f2c2df91c000001ebcda0614fa4946d2a109bd5871a10966510b9e7fa4e05d2848a4cbe49c46f1480000191aa89e90907e0c714f7c0d931dc6707d7ff2ab4e85f79769b8392d99bc95d1e000001a0877645bea04a236ec766cc0b2439c22c4c8e0fca9755ed070a1d21c4273414800001fda10c02d7a70e00000000000400000000000000dba70e000000000062951e00000000000c0000000000000008928c4e36a3e31e67a47863715990ca39b32f14e92e193012332e51446dc7a73c003a07fafc76a23266429049ca43b9f1ac2ea7baff0aedbcfa07919e2a5340b51700b3a05499134b0bbe3d012d160626b5f0fdc791899217165d626d9dc66443390100fc2bef68b8d16b76fc0feffbf3393a32286ea7e6666e8748309ef7d0b7fbef338091059fda9e88bf2a89403d20f2af347823e31ac552db4eae65575d80f3b9041380d0f38d630b0e842d0fb51b861a6c60e67a20a2764b15de6abc1e40dd33471a3d80c36dbdc6feb46adafcc2bf4c37e6107feeed32bbfe3af2a1941003302861801100bc8b436c1deadae25437f132237eff6b3a954fceef586a00260b92db5cb28f2c800008e2ffc821086f40a8662285c6ab2482c3885d2e28b20a88e23b46ff7ca20f4602809d9baf1f0152e4bc28e29afc414fcbf8304060f396063651ead6c019284f9f3800297a3a40994779a61b63eadd655b427117b05f07f47cb8d256a39d4f45fc6a3680d5ff4759f7eb5f10462028a5cbc839814be100a606b205928ffb44d0e8382012008b5c62596faa3bc1f0ca0418357a2e5e915bc2719d0fb5a56e4e16f10b2fb81280381934ff9e6add6e25c36533fe30d1f893c03d03bf16b49a33f33ad6ec2ba101803b334280c53054e41f89e35efa1a1aa1430634127b4ee9391920d1fba884f716002017d5d48c74ca40ffd95a35f6376b9acee517e03d961122dc0c7eabdfabee3b8000080699aaccf3ee0fe0d7e22ad0c215c552a5d3477ab0dbfb8378095808ca5d591d00a5619811b5f3714eae3ae0ab3d597b54a44421a98963545e0a877944ba7afc0e007f3b1abf88abecc7e3b1b808515a4d25debc2f01f7bd2915f3a4eccf3ac5993c80a9e6f2c56b2bd59484f9f3d6af5265eea2323152810cff8cc8d979fbf2ed6c29801a413354e385a6d1bc4418d363aad2973f476a1c5ae74f856d118f89735ec83c8064f084a0cdafb83c95e51b522d2a4dc24ba812b296c7589b06f4bf3029a0100a80b9a980eed407bc39e4c2fcc7a6e56d74cd9c49f7c62aca37570be118bd82af0e009f2c2ff61d0e13ba01684b7273b1db12eb44ee410bb0b02a9e87f290d79a27270000088c33b7d66769c41edcf5d773b93900744803b8cd35a16f9044540d2f544b5e1380bd68e7c8b8d4d9ca6f8a45318ffb0322f010f1c454a0112af8ae5bd02c6abd20805bf726856fc9bd383d13d870d209124c40d54665ac1bd2cf2873598999a2791680841f49e8f3d982a1e44e8992233a0708cb62067518d65bcfd634dec30f744c0080cb3ab237eee15b8d8b9f92fde2e5eece8b4b9222ab0299e4f474cde418be2f05804ad362238b7e88716c975afbd32f751109aa0a91def9e94ec59bb2510d6e8929007be79b17da6360aef1674a78ee92812462eccb1130d69e93b93652ef599f06108092e8b4331174ee76829933f35c16d1d5b340dbb92cb3972a474389ccf2ba46398000080e94b252e06b96ae885d5bfea2a18542f9b0b6e02b62fd06d3dcbe9d4f08d61280ea95c062a8809e1a0dee1fc3dbebf5062bc361a1f0d3f6a1c8d0c2680db8861d80a60167b40a86915770d8d9c9bd98ec1cdd47dbaeab8144f4d207e70d59906e2900c96a7a765f97c6cd4abb7fc547963034ab556cec065388f0371b8596450b0820808c0748b6a6f01540d735a6fdd9b153a7f4fa7b2097b562b3f2ae97b362f0bf1f00bd36e99b680050d51107020274aad1f69bbec61457204d8f1a3b44cce9130224004fdc0347aa3f8c8b7c814a709a9315e7d960fa6943e4adbeb7f6244701c1540d8049742ab2f558698eb7795c3820dcc9621cc481c18de3bcc2e3070bc9c85b9735000008dda7c1f68bd0c5ecd2a443c8a75d929c497a8d5a1876adfda969277cbe4efb0180f5358a32b1ef91f3c007052c7f17f44752cca1d52eabc1ab07a614221d2ebb070011f005fa07fe2f07a498f32e04cdf295c0850c5e14b7506112c5619062ed5c37800789cb02e14d2ac6bb58985dd5a067f12bf397f29fe691ddb972fb0bb955101c005803c20e84350e813ee8997c441172beda12692c88b2f7a5ae7a12890d2fb81c80f8ac0c8820c5bb61135fa716137ecdfa3603b696e6f750d19e501971839ccb0400ed921bce8e882f7d372ff696220259f34ef62331b4c8962b631c802418af6c3680a6a89c2a3f896e11ab184e0cd453c0a9a7e0cadff14549fc41265a16c07946000000089c8ff001a29d80c2b7e5da90dd8177f177e368f8e897a8e76adf767d655b3f158097eec429cdcf5c4aaf02bbc43a31bb2f966f132dd5e0bef525daabf03876370280cafa0fe272d5c07e76ff861e4e160b0530cff2f4fb983373dc9b455cdfb49b29001f78778a670792196261505aa25f9fd904be5cf3b0a23c9b096d1fcfcc09352180c62bce97c023486fd0cf0208c0f4d3a190b29eb1c3ef38fea456e41834def305803a35441208ce5db83ea493dfee103842179f206d19a63b5576d5ac1e75cbd815807085c7f75406c2b4821f508b4edaad9fe7386bb3da92558a23d2af64de75ac018041a7eef86c7e9bf3853bff91512e57021f2cc21e445c481f2b1b84e46ee65a10000008ed0358b6d85b0c62ce0825652825e4dc5457c7cc6ed1ff3fa80ec785cf4f090e80addd797a953af01bb934d44ac301ce50e6f4dc5d072c8b76877d59331af6fe3f809a8ad86c5967c0f3743d14b0739e0da9b2a50d9cf4b9034533c3e20414321a2a00f3d504874c0a33956f72cb46209806f30725385df0c1e63ff285fb9b0a9c2d0580112915e159801bea12d37b1c7e1d88c9dcec2f98c4498f2034a2334064c42b3a807c09f0d43909d3fc55951ef9cc9dcb48df0c0710eeff0680bfce03dfab10f908808ebc05ce77146da295b8cd1f55016c0a851192627c8f4a80dca67512553f3e0d00e803378486cb43631d05e69d19fb932d11cec6f79659f732df4907917817bf2b80000834586da86747456e176afccbed1b1eaba4077cf25e1957e84f83236393867f1a008c22e0890931f50206274af4476ed0d504742219f73f9934181b8e95c11c4b0f0087ba8cf32b0842f511d7831a02399f5708d6a9f833f65b561332dcc774130308009cf77e05cf2b3aad336dd2e27de07853480f8dd11c10851d5b608cc927bb6f2a00a3a037770c155a2f6f7beb227048d175aa378686dd1ad372e951c6d92a1c222500b53c2104263b067387ce4807e9c75d31782a12d28346c7ccbc9b288bf010410d003f6d01f346e5ca1c6f82a83dd5390f7be16bddc4721e9674f9cb64a34e82250200db9c0eda7e66e010a6892e2bafb21453ada66873603eeaeb25c66d32c6db712d8000082c26c188a30e0262c9e4eed094cfb37f8875a59c8af2f52dc4cf499d2a1e8c3d80b4ce91787794cf447c7f39a4cf30f11628aa28a1d2e4f995cb1c6e846bc6c434804ef8dc1f29028da05049835127273b42b359378baf35337626b3eff10ffbeb0c805505a32fa7100df2b8380226fed7467d0e6b0a68c184095fee1ceee4ad26da2780605e19cef403c83310bc3020be5190229ede441bd987acd2e3479b79ee53440180d62460f9624e7b0e8740a09183e9b21de2d11d3a04fc356b4724b0687b115c050071162d7becd091c543b41b9b50c3331af6f9028387e4ea2938b2b1a0a517560c001e794d111f41abfb4716105b477b7b2f6638e59dc97a19d68ec87286ef169007000008a73d62100c40ac5a9335d9113b53817c82c919d16a71763f08236586ad98d40c802700f9b126679dfea74dd3c4f7566ae3f08be4d434e815dfd43761d11198d11a000ff0558e7230d90775c7ed00d941bab15e8f5df2d2bc6c5c8db9f5b899d0f72e80007fd19cb916bcbc1ba054654c3e2ebd366869da51b39d110ede28af4d38ff2e00446902b0b27e83913a8fdd9598b9c6218f966c65dc66a90dea9c2082fa1d073900a5030daf387233bda44f6b61fc10ec58111931a9abf4f2698248a608788e793e80a49483d7b671848db3267deccabc55ed9a0a8dec3754a673a9465978784b633e80d3f077bfd46b532cf822304ce49493379a72e7fd9063cdd3619327635120d809800008916ed3f5b4bb8162c016850689e007c98277ff6d1f9599a3851553d3ede50931800a0f5569893f1133154bae7926e335eee1654028e35572fb3e1333c6d621f82c00f2b60679176ad6cd0a882c2aa831646d8fc53effee66f514127bffc094d976200010e62a4d8613da9962519bb0493c0b9e77f26cae339ed5547b181ea1a8fe7207802812eafdd5e28f8beb7a413c47f6cde17343221c549d9072f6459456491db93a00f708b2bd3352198963f4d86c429e285c39a17a9f49ec12714214e3dfa26ff613000c87d1cb8e8d0e62c015373bad3ac2a9722cd839184475d27d321b512175b42600e4238cc343935566d18425e70d9703f1c67d0ad270f99c5e53e9e6c0f577693d000002ffff00000000000000000000000000000000000000000000000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString("0a1c910e65d7feb6f1dd53342cc212584d24f0ce643dbba88312e5630714850b")))
    val mcRef: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(mcBlockHex), params, TestSidechainsVersionsManager(params)).get
    val mainchainBlockReferences = Seq(mcRef)
    val (forgerBox: ForgerBox, forgerMetadata)= ForgerBoxFixture.generateForgerBox(seed)
    val secretKey = VrfKeyGenerator.getInstance().generateSecret(seed.toString.getBytes(StandardCharsets.UTF_8))
    val publicKey = secretKey.publicImage()
    val genesisMessage =
      buildVrfMessage(intToConsensusSlotNumber(1), NonceConsensusEpochInfo(ConsensusNonce @@ "42424242".getBytes(StandardCharsets.UTF_8)))
    val vrfProof: VrfProof = secretKey.prove(genesisMessage).getKey
    val merklePath = MerkleTreeFixture.generateRandomMerklePath(seed + 1)
    val companion = getDefaultTransactionsCompanion

    val mainchainBlockReferencesData = mainchainBlockReferences.map(_.data)
    val mainchainHeaders = mainchainBlockReferences.map(_.header)

    val sidechainTransactionsMerkleRootHash: Array[Byte] = SidechainBlock.calculateTransactionsMerkleRootHash(Seq())
    val mainchainMerkleRootHash: Array[Byte] = SidechainBlockBase.calculateMainchainMerkleRootHash(mainchainBlockReferencesData, mainchainHeaders)
    val ommersMerkleRootHash: Array[Byte] = SidechainBlockBase.calculateOmmersMerkleRootHash(Seq())

    val blockVersion: Block.Version = 1: Byte

    val unsignedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
      blockVersion,
      parentId,
      timestamp,
      forgerMetadata.forgingStakeInfo,
      merklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      0,
      new Array[Byte](32),
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    val signature = forgerMetadata.blockSignSecret.sign(unsignedBlockHeader.messageToSign)

    val signedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
      blockVersion,
      parentId,
      timestamp,
      forgerMetadata.forgingStakeInfo,
      merklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      0,
      new Array[Byte](32),
      signature
    )

    val data = new SidechainBlock(signedBlockHeader, Seq(), mainchainBlockReferencesData, mainchainHeaders, Seq(), companion)

    val hexString = BytesUtils.toHexString(data.bytes)
    println(hexString)
  }
}
