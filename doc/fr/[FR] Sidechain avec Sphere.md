<!-- wp:paragraph -->
<p><br>L'objectif est de déclarer une sidechain grâce au wallet Sphere By Horizen puis de procéder à une transaction entre la mainchain et la sidechain précédemment créée. </p>
<!-- /wp:paragraph -->

<!-- wp:heading -->
<h2>Étapes préliminaires</h2>
<!-- /wp:heading -->

<!-- wp:paragraph -->
<p>Avant de commencer il vous faut récupérer la version testnet du wallet Sphere By Horizen ici : <a href="https://github.com/ZencashOfficial/Sphere_by_Horizen_Sidechain_Testnet/releases/tag/desktop-v2.0.0-beta-sidechain-testnet" target="_blank" rel="noreferrer noopener">https://github.com/ZencashOfficial/Sphere_by_Horizen_Sidechain_Testnet/releases/tag/desktop-v2.0.0-beta-sidechain-testnet</a><br>Vous prenez la version correspondante à votre système d'exploitation : exe pour Windows ou deb pour Linux. N'oubliez pas de vérifier que votre fichier ne soit pas corrompu :</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>$ sha256sum Sphere_by_Horizen_Sidechain_Testnet-2.0.0-beta.deb
be976e8d13338670916367adb8ee0da6bbd3d9582574ebee5cab6046d5d0fe79
</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Créez vous un compte, créez vous un wallet, vous n'avez pas forcément besoin de noter la phrase de backup. En effet ici on utilise seulement le testnet avec des ZEN factices (tZEN). Et donc si l'on en perd l'accès cela n'aura pas trop de conséquences... </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Laissez Sphere ouvert et vérifiez qu'il  soit bien en train de se synchroniser à la blockchain avant de vous lancer à la prochaine étape. En effet la synchronisation prend un certain temps.</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>L’avantage d’utiliser Sphere by Horizen est que l’on n'aura pas besoin de lancer notre propre noeud <strong>Zend_oo</strong>, en effet Sphere (dans la version testnet) fait aussi office de noeud. </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>La deuxième étape préliminaire est de récupérer des tZEN grâce au Horizen Early Adopter Program (HEAP) : <a href="https://heap.horizen.io/" target="_blank" rel="noreferrer noopener">https://heap.horizen.io/</a>. Rentrez l'adresse du wallet que vous venez de créer pour recevoir vos premiers tZEN !</p>
<!-- /wp:paragraph -->

<!-- wp:image {"align":"center","id":81,"sizeSlug":"large"} -->
<div class="wp-block-image"><figure class="aligncenter size-large"><img src="https://cryptochu.fr/wp-content/uploads/2020/09/tZEN.png" alt="" class="wp-image-81"/></figure></div>
<!-- /wp:image -->

<!-- wp:paragraph -->
<p>Vous devriez les voir apparaître dans votre wallet assez rapidement. </p>
<!-- /wp:paragraph -->

<!-- wp:heading -->
<h2>Installation Java8 et Maven</h2>
<!-- /wp:heading -->

<!-- wp:paragraph -->
<p>Pour les trois parties qui vont suivre j'ai repris le tutoriel de Xavier Garreau qui expliquait comment lancer une sidechain en ligne de commande avec un noeud Zend_oo: <a href="https://mescryptos.fr/les-sidechains-horizen-ma-premiere-sidechain/" target="_blank" rel="noreferrer noopener">https://mescryptos.fr/les-sidechains-horizen-ma-premiere-sidechain/</a></p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Il y a tout d'abord quelques prérequis à installer :</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>$ sudo apt-get update
$ sudo apt-get install -y software-properties-common
$ wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | sudo apt-key add -
$ sudo add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
$ sudo apt-get update
$ sudo apt-get install adoptopenjdk-8-hotspot
$ sudo apt-get install maven</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Je vérifie que tout s'est bien déroulé et que je dispose bien de Java8 et de Maven.</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>$ java -version
openjdk version "11.0.7" 2020-04-14
OpenJDK Runtime Environment (build 11.0.7+10-post-Ubuntu-2ubuntu218.04)
OpenJDK 64-Bit Server VM (build 11.0.7+10-post-Ubuntu-2ubuntu218.04, mixed mode, sharing)

$ mvn --version
Apache Maven 3.6.0
Maven home: /usr/share/maven
Java version: 1.8.0_265, vendor: AdoptOpenJDK, runtime: /usr/lib/jvm/adoptopenjdk-8-hotspot-amd64/jre
Default locale: fr_FR, platform encoding: UTF-8
OS name: "linux", version: "5.3.0-40-generic", arch: "amd64", family: "unix"
</code></pre>
<!-- /wp:code -->

<!-- wp:heading -->
<h2>Compilation des éléments du Sidechains-SDK</h2>
<!-- /wp:heading -->

<!-- wp:paragraph -->
<p>Je commence par récupérer le Sidechains-SDK, je me place dans le répertoire ainsi créé et je lance la compilation :</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>$ git clone https://github.com/ZencashOfficial/Sidechains-SDK.git
$ cd Sidechains-SDK
$ mvn package</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Si Maven rapporte une erreur disant qu’il ne trouve pas&nbsp;<code>/usr/lib/jvm/adoptopenjdk-8-hotspot-amd64/jre/bin/javac</code>, c’est qu’il ne cherche pas au bon endroit (dans le sous répertoire&nbsp;<code>jre</code>&nbsp;au lieu du répertoire du JDK qui est un étage plus haut). Il faut corriger ça en forçant la variable d’environnement&nbsp;<code>JAVA_HOME</code>&nbsp;et relancer :</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>$ export JAVA_HOME=/usr/lib/jvm/adoptopenjdk-8-hotspot-amd64/
$ mvn package</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Après ceci, tout doit bien se passer et vous devriez voir apparaître le résumé suivant : </p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>&#91;INFO] -----------------------&lt; com.horizen:Sidechains >-----------------------
&#91;INFO] Building Sidechains 0.2.4                                      	&#91;4/4]
&#91;INFO] --------------------------------&#91; pom ]---------------------------------
&#91;INFO] ------------------------------------------------------------------------
&#91;INFO] Reactor Summary for Sidechains 0.2.4:
&#91;INFO]
&#91;INFO] io.horizen:sidechains-sdk .......................... SUCCESS &#91;05:37 min]
&#91;INFO] sidechains-sdk-simpleapp ........................... SUCCESS &#91; 51.351 s]
&#91;INFO] sidechains-sdk-scbootstrappingtools ................ SUCCESS &#91; 22.454 s]
&#91;INFO] Sidechains ......................................... SUCCESS &#91;  0.000 s]
&#91;INFO] ------------------------------------------------------------------------
&#91;INFO] BUILD SUCCESS
&#91;INFO] ------------------------------------------------------------------------
&#91;INFO] Total time:  06:53 min
&#91;INFO] Finished at: 2020-09-21T18:32:52+02:00
&#91;INFO] ------------------------------------------------------------------------
</code></pre>
<!-- /wp:code -->

<!-- wp:heading -->
<h2>Bootstrapping </h2>
<!-- /wp:heading -->

<!-- wp:paragraph -->
<p>A présent, nous allons récupérer les informations nécessaires à la déclaration d'une sidechain dans Sphere. </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph {"align":"left"} -->
<p class="has-text-align-left">Pour cela nous allons utiliser l'outil de Boostrapping, en étant toujours dans le répertoire Sidechains-SDK : </p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>$ java -jar tools/sctool/target/sidechains-sdk-scbootstrappingtools-0.2.4.jar
Tool successfully started...
Please, enter the command:
</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Cet outil permet de saisir des commandes et leurs arguments au format JSON. Les réponses sont également au format JSON</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Premièrement, nous allons avoir besoin d'une paire de clé ed25519 et d'une paire de clé Vrf, ces paires de clés seront utilisés pour initialiser ma sidechain. </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Pour générer ces clés nous avons besoin d'une "seed", c'est une chaîne de caractère que l'on passe en entrée à notre générateur pour l'initialiser et qui nous fournit une sortie pseudo-aléatoire.</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Ici, toutes les "seed" sont des chaînes de 42 caractères (nombre de ZEN pour un securenode :)) issues d'un générateur de mot de passe aléatoire. </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Attention à bien conserver les réponses de l'outil dans un fichier texte pour pouvoir les utiliser pour la création de la sidechain. </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>C'est parti pour générer notre clé ed25519 :</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>generatekey {"seed":"BUvQVGnY5PGG99q4BHZZar5ab7B6X4AMFnKheg5B7m"}</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>La réponse obtenue est la suivante :</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>{
"secret":"00c98020dadad8b2fb6672ed378fc00f44d4f7121e100fce4e2568111da85d516aefb9e869086b82625b666e4270bc09d45d52857da282a1132e5cbaf18fe6d229",
"publicKey":"efb9e869086b82625b666e4270bc09d45d52857da282a1132e5cbaf18fe6d229"
}</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Nous aurons seulement besoin de la <strong>publicKey</strong> pour la suite des opérations.</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Ensuite passons à la génération de la clé Vrf : </p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>generateVrfKey {"seed":"AEvEHXJw63JtwcauKKz3km8MfHCkGeHXuKBeH32hD5"}</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Voici le résultat :</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>{
"vrfSecret":"0300000060fefec09a4fc02abbaa938e5d0488194a7a571159592ddbb068d342cd7305ea0733ab2dbc3e8c43b7883b4b6e9468a269df7de57b96661cf7f07320befd5410caeca5793d76fad0a8a1c324945051853869c64cc1b15eb4020b2d0ebe8b2100008d96380a90b295176152411b00b049cc282413fc6eac845cb4ff60e1f2e3865954f779540b571f4cf305074cb80a4538a77fcede11c07cb3ba358a87e2a9cca8ba6ee013d1468533060e05c8cd277b5b56f4f9ee4b9341fecfe4d211bb120100570b6a7f53db896d971dc2b58232600ce67ac243fb931278ac87f11db2a9bdd991e5e7a2af8fcb151212b49b5b077f0c54c4d312a159c0382af2591d85bc40ef220625647343ae1c74390ac8459031eaac4737b9245118960a143b06d185010000",
"vrfPublicKey":"8d96380a90b295176152411b00b049cc282413fc6eac845cb4ff60e1f2e3865954f779540b571f4cf305074cb80a4538a77fcede11c07cb3ba358a87e2a9cca8ba6ee013d1468533060e05c8cd277b5b56f4f9ee4b9341fecfe4d211bb120100570b6a7f53db896d971dc2b58232600ce67ac243fb931278ac87f11db2a9bdd991e5e7a2af8fcb151212b49b5b077f0c54c4d312a159c0382af2591d85bc40ef220625647343ae1c74390ac8459031eaac4737b9245118960a143b06d185010000"
}</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Cette fois ci nous avons seulement besoin de garder la <strong>vrfPublicKey</strong> pour la suite des opérations.</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Enfin je dois générer des données pour la validation des transactions entre la sidechain et la mainchain. La valeur du paramètre keycount est fixée à 7 pour l’instant. Pour ce qui est de threshold, il doit être inférieur ou égal à 7. La documentation de SimpleApp utilise 5. Donc, j’ai fait de même.</p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>generateProofInfo {"seed":"ap7zGSt79nS3BJwDY3A5JKEVNbsnxpwcPc7rEZnXL7", "keyCount":7, "threshold":5}</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Voilà le résultat : </p>
<!-- /wp:paragraph -->

<!-- wp:code -->
<pre class="wp-block-code"><code>{
   "threshold":5,
"genSysConstant":"50ea84a00d5851a192c0c870a5f5340714ef7644c9552f7ccf1aa0b18c247d0108f91d86094b33c10281c9cc6e0781bfd6f6499e52426056b974ed797cbd95481e0d5bbf47a887f8728282a98d30bb505aff24e23911af707c0c3966ae3a0100",
   "verificationKey":"5e7b462cc84ae0faaa5884bd5c4a5a5edf13db210599aeeb4d273c0f5f32967b7071ce2b4d490b9f08f6ce66a8405735c79197cd6773d1c5aeb2a38da1c102df07b05879c77198e5aafa7feed25d4137e86b3d98d9edd9547a460f1615b10000ee9570fbffedd44170477b37500a0a1cb3f94b6361f10f8a68c4075fbc17542d7174b3d95e12ddb8aea5d6b6c53c1df6c8f60010cd2e69902ba5e89e86747569463a23254730fc8d2aabf39648a505df9dcce461443b181ef3eda46074070000550836db2c97820971db6b1421e348d946ed4d3f255295abea46556615e3123de33ec56f784f70302901a4bc10c79c6a8b1e32477aeff9fba75876592981b678fc5a2703ac0b3055e567a6cb1ebab578fc4f9121fd968680250696cb85790000078fcfb60bdfc79aa1e377cb120480538e0236156f23129a88824ca5a1d77e371e5e98a16e6f32087c91aa02a4f5e00e412e515c3b678f6535141203c6886c637b626a2ada4062d037503359a680979091c68941a307db6e4ed8bc49d21b00002f0e6f88fb69309873fdefb015569e5511fb5399295204876543d065d177bf36ab79183a7c5e504b50691bc5b4ed0293324cfe2555d3fc8e39485822a90a91afcd4ef79ec3aefbd4cbe25cbccd802d8334ce1dce238c3f7505330a14615500001f89fbe1922ab3aa31a28fd29e19673714a7e48050dee59859d68345bb7bee7d5e888d8b798a58d7c650f9138304c05a92b668294c6114185ccb2c67ce0bbbb7e1dcbb6d76f5cacd7c9732a33b21d69bd7a28c9cca68b5735d50413862bc0100308bb0dd0bd53f3d1134966702dd3c7cc8b58b270a6996a646493250b0d5f3978d0c971f8fa7a0c958f3efe2fa5269244973fafb701c2eb66dd25901f93d677ab6c538c1ed11f115e52d3f2c7087ea40c3e8cd089376baa38842e9429b5f0000d19a8d874d791f952f13d3c8ecd92e44009c09815e5ae6a8e5def7ea52fe3de4accfb5ba2aa401fbcec14b069cd0dc0f66ab025b45ef9831a26acf58673db7487043654e7980fcb2b6c1bd7593a4dfff810436f653e309121c7ccf2df70b010000732254ec6df184be360cd9ed383ed7c8c236d7761cfc0ce4e7f0cac5a06f4edab9cfc75a7dc1449c0e18ed9564c974c2e1b6847c637f74e5d391cbc80fc6e672ffd66b5ce4fb73bda8359ab8a0ea1e855df1e07d82f93c935c7e1a9a55c5000065efdbb7c3e82291a482b2f24cbd46f4dd02c370cf6dcfe8fb3c00b8b004b5ad51369b1f1b134a824d1f16d72ca6a27ba2d6190150329139cf2c6d9e5a14722f8d39b96b882c1f60a7b230e929819e2abe1cd9d7f3e8c726b1a94d20c8010100732c396eca6ffa1bf851cef449f2f087edd93e4f641b4bd93a482d9f129e675aedb688993d4e2cee824d2803301364ba10fbb66895927adb53bad8aefe8a1caab6f4ccb45883e414a1223ac7f90a89087cd752dfa0c7b3e19bbae000edd5000028d1d23c627d1252d2a2a20a246af2280f50e3fde667873aadd9893ba6833118358398e7428e717128f764714a8d52b090c1f554f58e25ea815338d7bc7326c949567e74f2f2ab3c88f5075fea75594608b8937c9059a42d712ffbd1bd980100000000000250c1a474689e375a309446e5cdd3a0c26cecdcff5c7b8cdc0728868983f1a35a49e3a1bae6f969c3d47356c08d3d169d2c0a2be908d82cd35f41a23d8c2924a9f790ab3a00d53061d440a176670d6a32de2ecd19cf8a9774729c09a6ea4d0100d8838bf55d95521291da12294b302c66042eda0dc2acc79360a1fdd8c9a366fa790c52bf926c2d96b5ba88a3a443487c5235f7c476f350c2101cfbe3bd0361dd291ebc5e42c097a158704b71006886a3662ca6db7d816b4ad12444835d89000000795ce2b34aef921ccb3d9b9695f5d3fe0a03743c955cfcf01f8a1815a7c8b03de85fe15201d4b4b6f401cb334a6988ea5bde8986a468c47c3c6a5ae96a3160ff15e06699ea82bd40c0d5547fe1be77af7817861bbfcca3f4232f05a9cec800006c216565cee4d57b32d2d70bb3cb8d4a967c0eb5d7137b2ec58466f3d4d3b5375e4baa823bcc29c6ad877d9708cd5dc1c31fa3883a80710431110c4aa22e97b67fa639f54e86cfab87187011270139df7873bed12f6fb8cd9ab48f389338010000",
   "schnorrKeys":&#91;
      {
         "schnorrSecret":"0400000060fd7322008692445b10e294267ab04205a282b4652588499779f2f542e87f5dfd18c128989077973c136c5ed13790d913cdfe679d5c836e0a0b7ee0c4a23def50a7d0aefeb5ea1cb9d2d3dae526e8a9c0d73d961363cca2c4de2f79e2b64c01000ec49fb90f934dca9845cd10640437b467998f33fb2cf31ef143dc44e967d9154fe4323916d8830d724e857d58e17007f05a50af207e129e924cec7df059219e7e90935fe61d5b19d03e80f16e7c4db275243e0203b6f03b797f9334b8ec00005981a3b4fdc4c3ec09d293332100073ff1d98451b76993a1f5e02c070fddc0128d79cc8c2f7e745261b3bf3ac1d1cdfe5f2052b95223245690095964b8859f1aa5346f89e15d7b824bfc6875a2115c5fbd709160ac28ae33b3a2fe72bcf0000000",
         "schnorrPublicKey":"0ec49fb90f934dca9845cd10640437b467998f33fb2cf31ef143dc44e967d9154fe4323916d8830d724e857d58e17007f05a50af207e129e924cec7df059219e7e90935fe61d5b19d03e80f16e7c4db275243e0203b6f03b797f9334b8ec00005981a3b4fdc4c3ec09d293332100073ff1d98451b76993a1f5e02c070fddc0128d79cc8c2f7e745261b3bf3ac1d1cdfe5f2052b95223245690095964b8859f1aa5346f89e15d7b824bfc6875a2115c5fbd709160ac28ae33b3a2fe72bcf0000000"
      },
      {
         "schnorrSecret":"0400000060f3d988bdaa692c0ea7483462e12b7753bbf14dcea7b67e793caea17d6e1bc44479d133b2a13cf83dee9456c49c6a061139e1ed0c1cfc961febb5a7559390bbd929de59d64ebf0c46bc5895bde442bb8f0c46056f4a2ed0b798929cf6b01e01007e01299f32e6667851f7dd7625eacd776d2af6fa0da40a3688bdaad101e2edaeadfcd391dc585dc16f7a5436aef6b2254dd9d4fba77f3cf7b980d09076b2bfb93b2b10d50a4efc45c221ce6cd19d989542a915f581c2937209d19fa6ff3c0000083a2fe5cd04673a69f94bc7fce4011c99ebde1de5a863d1e54029b603bd1b5445715f54d22f37e7d349f6cb1ff9f938046bd3efb19ece0d1f60c67171a540aa7ceb06379ea65ee3bef9dcc952cd018ba65398c64171f951af02cf3d34a8000000",
         "schnorrPublicKey":"7e01299f32e6667851f7dd7625eacd776d2af6fa0da40a3688bdaad101e2edaeadfcd391dc585dc16f7a5436aef6b2254dd9d4fba77f3cf7b980d09076b2bfb93b2b10d50a4efc45c221ce6cd19d989542a915f581c2937209d19fa6ff3c0000083a2fe5cd04673a69f94bc7fce4011c99ebde1de5a863d1e54029b603bd1b5445715f54d22f37e7d349f6cb1ff9f938046bd3efb19ece0d1f60c67171a540aa7ceb06379ea65ee3bef9dcc952cd018ba65398c64171f951af02cf3d34a8000000"
      },
      {
         "schnorrSecret":"04000000607acb921ed283ec0c46ae71d8e5d757b2e31eec812723e65bc15abb2f80684450eae85b1b0854bd9f021957a97c0c2cb9f9cbe8a901759d078fab36c03a74cc67ecb5da4ad177b3bc1db73e9c4d9ac4ac45d1f215c16f721150d6c7ed0f890100a3aa11ac70ae613617cdd7cf1c67111124d0cfa0ee27b9a04eda6f1af6745179dbe23aa6fa8634c19cb9fa795ccc0522bc992ebd21d972c84feef3a933bcb237cbf4359cf7180011716aea6b1c3169d12c567526192528150d2bcc67fdae0100902d77a9dfa572dfddd5182676f028f39ed3a480f4cb4a255300e49ae5a5fccca5bcce468700250e068ff5a8b3f8c93154c88e94fa22a91c9071d67875a2894704aecd5b89b004be0fb1aa7dc39ecb58ae8f3b704526693953ab6794f775010000",
         "schnorrPublicKey":"a3aa11ac70ae613617cdd7cf1c67111124d0cfa0ee27b9a04eda6f1af6745179dbe23aa6fa8634c19cb9fa795ccc0522bc992ebd21d972c84feef3a933bcb237cbf4359cf7180011716aea6b1c3169d12c567526192528150d2bcc67fdae0100902d77a9dfa572dfddd5182676f028f39ed3a480f4cb4a255300e49ae5a5fccca5bcce468700250e068ff5a8b3f8c93154c88e94fa22a91c9071d67875a2894704aecd5b89b004be0fb1aa7dc39ecb58ae8f3b704526693953ab6794f775010000"
      },
      {
         "schnorrSecret":"040000006006a06e9eacff72b2c623b93755779650961dc1ceabff0305b30700bde797de1b0acd33d70f2c386a0e74f1b01530e5c74e57a952fd6cc692dc6d793da9a0f3ba5e806e9e03ac246fac69f6196750f0e0869842389b03bf1830c2a4acf4aa000062094c71b9c7e6417a89b09b935e2273e1ec3bfbb98e5c0ee8ee66501bb4bb7b784381e37800f4a49de67aac24fa719688adf58895a927b98af6fcf47564e184a68f07e6f453f5b90bee6cf9e079ecba5ab51e29055815d28adc1acb9a98000048d3155ec3ff74bec4169d89225f9d94b61b123edd4f1cc337af7ae15a65d2b5b63fce322c3c6a51cf944ce417377d49d56c1cdfe80d6c3829d8813b8c0bb758d6c4cb582e9fe711e2f18838803636b4a912732deb5f7810b2dd16977358010000",
         "schnorrPublicKey":"62094c71b9c7e6417a89b09b935e2273e1ec3bfbb98e5c0ee8ee66501bb4bb7b784381e37800f4a49de67aac24fa719688adf58895a927b98af6fcf47564e184a68f07e6f453f5b90bee6cf9e079ecba5ab51e29055815d28adc1acb9a98000048d3155ec3ff74bec4169d89225f9d94b61b123edd4f1cc337af7ae15a65d2b5b63fce322c3c6a51cf944ce417377d49d56c1cdfe80d6c3829d8813b8c0bb758d6c4cb582e9fe711e2f18838803636b4a912732deb5f7810b2dd16977358010000"
      },
      {
         "schnorrSecret":"0400000060e49eaebf7cffd0d909d65c850a1e2d03df35ebc95b065968b482ce721f0f6bc785f2d2c627d1752c84ced959c4cf7d3e975e3ab704298e6b846ad3cbda94cc7adf97b8520e8428de7a7b2940c6d7e4cf839632a56243be22210a4ec1b96e01007a06f56851f9a8962e541788d8cf1a15650f1e8affc0a5fc5e8ab4c34a77cd1ff481716b17125fb900b445cd1293aa02d8c46b7a243a977c260f1a5769ef8ae0e98815c62640a766455d0fc929d38090fb1e5ca267d78003a5bd8a29c0f9000053d240ee5cf840626453491ecf7ec8a87b668372c4ea34c6519d8892482dc27f9cca8b6fe2f28b0736f5d6fe5f6570318e23e4c16f867ddc1d98e06dbdd383df05701f37d2d43137b2e128b7d7ca63d5fa2a95a7261173384578bb562783000000",
         "schnorrPublicKey":"7a06f56851f9a8962e541788d8cf1a15650f1e8affc0a5fc5e8ab4c34a77cd1ff481716b17125fb900b445cd1293aa02d8c46b7a243a977c260f1a5769ef8ae0e98815c62640a766455d0fc929d38090fb1e5ca267d78003a5bd8a29c0f9000053d240ee5cf840626453491ecf7ec8a87b668372c4ea34c6519d8892482dc27f9cca8b6fe2f28b0736f5d6fe5f6570318e23e4c16f867ddc1d98e06dbdd383df05701f37d2d43137b2e128b7d7ca63d5fa2a95a7261173384578bb562783000000"
      },
      {
         "schnorrSecret":"0400000060a02fe2190758cbc1bb06a778149d82c6810a69f6782893d4eca67d85eea70d0e215eed6154a6e6ef61040de49379ce15c3546c47d92d064c97e3a556f11edb507001a0c0b747551f28385c5cd1e1b669c2c5b5f192f07c4d11e98b0cae050100d99bd9fa622aedf640b2a28484fd80f6bd2d272a6863b3e1d916591a43b82bb4417e8f32a41d98aae99b122efaf69e63c3c168f4b369de567e6b83abe56785758c81e7e6cb7d39ad36f607cd8d973a70dc896fa79ccc4c8b1212c5bb520c0100b1cb154c6d470ecad627324dd459d42b4d490a3744f9deceb1a58befed3315e493abb61fb68c946e068b90602f689937044c84fdf81ec711a0b937ef2276c9703cbce6c0b40a88c0b8d7f8b129f3e3bc829fd0d6fb8467d76c47277de507010000",
         "schnorrPublicKey":"d99bd9fa622aedf640b2a28484fd80f6bd2d272a6863b3e1d916591a43b82bb4417e8f32a41d98aae99b122efaf69e63c3c168f4b369de567e6b83abe56785758c81e7e6cb7d39ad36f607cd8d973a70dc896fa79ccc4c8b1212c5bb520c0100b1cb154c6d470ecad627324dd459d42b4d490a3744f9deceb1a58befed3315e493abb61fb68c946e068b90602f689937044c84fdf81ec711a0b937ef2276c9703cbce6c0b40a88c0b8d7f8b129f3e3bc829fd0d6fb8467d76c47277de507010000"
      },
      {
         "schnorrSecret":"040000006045be98894f8ffac1d4b53c185e6c4b7a50692f73254f877c1d4cc5e81530fc233c15f3e5c176a4323c17be24b6ff101a9d2e746501f57d4ae805e9a3c1ed735409a78e161bf6ed269dfc73c50d3a54eea4e2552c3ce6c2704498fc57533600009f466bba11c20ba8459d0cc9124562bd0630c9e59998c283e69ba222bb653d0ef950f1084504c7ed46c53fe46a8a522f3d31ca8b21e25be64b772ec049c6722669727913a090c4ad9d3cc5027588f1356a54ea81baf9df8cb125c365a8350100e53641d67fefe267124278b510892740e19861fcd211e0d759a6d1f3ca033bea226c259468ea33252c2eb970b850f81796f2a4179c639f26ee80a114829229d53a13b2727e141189179438381dee1053b1ab3baafeaffede235f793d9fef000000",
         "schnorrPublicKey":"9f466bba11c20ba8459d0cc9124562bd0630c9e59998c283e69ba222bb653d0ef950f1084504c7ed46c53fe46a8a522f3d31ca8b21e25be64b772ec049c6722669727913a090c4ad9d3cc5027588f1356a54ea81baf9df8cb125c365a8350100e53641d67fefe267124278b510892740e19861fcd211e0d759a6d1f3ca033bea226c259468ea33252c2eb970b850f81796f2a4179c639f26ee80a114829229d53a13b2727e141189179438381dee1053b1ab3baafeaffede235f793d9fef000000"
      }
   ]
}</code></pre>
<!-- /wp:code -->

<!-- wp:paragraph -->
<p>Ici nous devons récupérer deux chaînes que nous utiliserons plus tard, il s'agit de <strong>genSysConstant</strong> et de la <strong>verificationKey</strong>.</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>A présent, nous avons récupéré toutes les informations dont nous avions besoin, vous pouvez fermer l'outil de Bootstrapping avec la commande <em>exit</em>. <br>Nous en avons aussi terminé avec la ligne de commande, nous allons enfin passer sur une interface graphique.</p>
<!-- /wp:paragraph -->

<!-- wp:heading -->
<h2>Déclaration de la sidechain en interface graphique </h2>
<!-- /wp:heading -->

<!-- wp:paragraph -->
<p>C'est parti pour la création de la sidechain, pour cela il faudra au préalable avoir synchronisé le testnet de la blockchain en totalité pour avoir accès à l'onglet "Sidechains TESTNET".</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Vous pouvez ensuite commencer la création de la sidechain. Pour cela vous devez avoir des tZEN sur votre wallet sur la mainchain, vous pourrez donc choisir combien de tZEN vous souhaitez envoyer sur la sidechain lors de la création. </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Après cela vous devez nommer votre sidechain, ce sera plus facile pour la retrouver si jamais vous en avez plusieurs.</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Enfin vous devez remplir les 4 derniers champs avec les informations obtenues lors de l'étape de Boostrapping :</p>
<!-- /wp:paragraph -->

<!-- wp:list -->
<ul><li>Sidechain creation address : c'est la <strong>publicKey</strong> obtenue en réponse à l'appel generateKey lors du bootstrap. C'est aussi la première adresse sur la sidechain. </li><li>wCertVk : c'est la <strong>verificationKey </strong>obtenue en réponse à l'appel generateProofInfo. Il doit avoir une longueur de 3088 caractères hexadécimaux.</li><li>Constant data : c'est le <strong>genSysConstant </strong>aussi obtenue en réponse à l'appel generateProofInfo. Il doit avoir une longueur de 192 caractères hexadécimaux.</li><li>Custom data : c'est la <strong>vrfPublicKey</strong> obtenue en réponse à l'appel generateVrfKey.</li></ul>
<!-- /wp:list -->

<!-- wp:image {"align":"center","id":84,"sizeSlug":"large"} -->
<div class="wp-block-image"><figure class="aligncenter size-large"><img src="https://cryptochu.fr/wp-content/uploads/2020/09/Creation-Sidechain.png" alt="" class="wp-image-84"/></figure></div>
<!-- /wp:image -->

<!-- wp:paragraph -->
<p>La sidechain est donc créée, il faudra attendre un certain nombre de confirmations de la part du réseau pour qu'elle soit finalement active. </p>
<!-- /wp:paragraph -->

<!-- wp:group -->
<div class="wp-block-group"><div class="wp-block-group__inner-container"><!-- wp:gallery {"ids":[92,96]} -->
<figure class="wp-block-gallery columns-2 is-cropped"><ul class="blocks-gallery-grid"><li class="blocks-gallery-item"><figure><img src="https://cryptochu.fr/wp-content/uploads/2020/09/sidechain-immature.png" alt="" data-id="92" class="wp-image-92"/></figure></li><li class="blocks-gallery-item"><figure><img src="https://cryptochu.fr/wp-content/uploads/2020/09/sidechain-mature.png" alt="" data-id="96" data-full-url="https://cryptochu.fr/wp-content/uploads/2020/09/sidechain-mature.png" data-link="https://cryptochu.fr/?attachment_id=96#main" class="wp-image-96"/></figure></li></ul></figure>
<!-- /wp:gallery --></div></div>
<!-- /wp:group -->

<!-- wp:paragraph -->
<p>Et voilà, vous avez votre <strong>sidechain </strong>active et déclarée sur la <strong>mainchain </strong>!</p>
<!-- /wp:paragraph -->

<!-- wp:heading -->
<h2>Forward transfer : de la mainchain à la sidechain</h2>
<!-- /wp:heading -->

<!-- wp:paragraph -->
<p>Pour finaliser cet article, nous allons procéder à une transaction de la mainchain à la sidechain.</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Pour cela, il faut qu'il vous reste encore des tZEN dans votre wallet sur la mainchain. Sélectionnez l'option "Send" à partir de votre adresse qui contient des tZEN. </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>En haut à droite, vérifiez bien d'avoir coché le bouton "Sidechain transaction" pour voir apparaître la possibilité d'effectuer une transaction vers une sidechain. Sélectionnez votre sidechain.</p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Pour l'adresse de réception, il s'agit de l'adresse sur laquelle on a envoyé des tZEN pour la création de notre sidechain. C'est aussi la <strong>publicKey</strong> obtenue en réponse à l'appel generateKey lors du bootstrap.</p>
<!-- /wp:paragraph -->

<!-- wp:image {"id":90,"sizeSlug":"large"} -->
<figure class="wp-block-image size-large"><img src="https://cryptochu.fr/wp-content/uploads/2020/09/Forward-transfer-transaction-2.png" alt="" class="wp-image-90"/></figure>
<!-- /wp:image -->

<!-- wp:image {"id":99,"sizeSlug":"large"} -->
<figure class="wp-block-image size-large"><img src="https://cryptochu.fr/wp-content/uploads/2020/09/transaction-history.png" alt="" class="wp-image-99"/></figure>
<!-- /wp:image -->

<!-- wp:paragraph -->
<p>La transaction apparaît bien dans notre historique des transactions de notre adresse sur la mainchain, tout comme la transaction de création de la blockchain. </p>
<!-- /wp:paragraph -->

<!-- wp:gallery {"ids":[98,91]} -->
<figure class="wp-block-gallery columns-2 is-cropped"><ul class="blocks-gallery-grid"><li class="blocks-gallery-item"><figure><img src="https://cryptochu.fr/wp-content/uploads/2020/09/forward-transaction.png" alt="" data-id="98" data-full-url="https://cryptochu.fr/wp-content/uploads/2020/09/forward-transaction.png" data-link="https://cryptochu.fr/?attachment_id=98#main" class="wp-image-98"/></figure></li><li class="blocks-gallery-item"><figure><img src="https://cryptochu.fr/wp-content/uploads/2020/09/final-balance-sidechain.png" alt="" data-id="91" class="wp-image-91"/></figure></li></ul></figure>
<!-- /wp:gallery -->

<!-- wp:paragraph -->
<p>Une fois que la transaction a reçu assez de confirmations, on peut vérifier que le solde a été crédité sur la sidechain. </p>
<!-- /wp:paragraph -->

<!-- wp:paragraph -->
<p>Et voilà, vous avez donc appris à déclarer votre sidechain grâce à l'interface graphique de Sphere By Horizen ainsi qu'à réaliser des transactions entre la mainchain et votre sidechain. </p>
<!-- /wp:paragraph -->
