package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.route.WalletBaseErrorResponse._
import io.horizen.api.http.route.WalletBaseRestScheme._
import io.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiError, SuccessResponse}
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.json.Views
import io.horizen.node._
import io.horizen.params.NetworkParams
import io.horizen.proposition.{Proposition, VrfPublicKey}
import io.horizen.secret._
import io.horizen.transaction.Transaction
import io.horizen.utils.BytesUtils
import io.horizen.{SidechainNodeViewBase, SidechainTypes}
import sparkz.core.settings.RESTApiSettings

import java.io.{File, PrintWriter}
import java.util
import java.util.{Scanner, Optional => JOptional}
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

abstract class WalletBaseApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](
                                   override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef,
                                   sidechainSecretsCompanion: SidechainSecretsCompanion
                        )(implicit val context: ActorRefFactory, override val ec: ExecutionContext, override val tag: ClassTag[NV])
  extends SidechainApiRoute[TX, H, PM, FPI, NH, NS, NW, NP, NV] with DisableApiRoute {


  val walletPathPrefix: String = "wallet"

  /**
   * Create new Vrf secret and return corresponding public key
   */
  def createVrfSecret: Route = (post & path("createVrfSecret")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateKey]) { _ =>
          withNodeView { _ =>
            val secretFuture = sidechainNodeViewHolderRef ? ReceivableMessages.GenerateSecret(VrfKeyGenerator.getInstance)
            Await.result(secretFuture, timeout.duration).asInstanceOf[Try[VrfSecretKey]] match {
              case Success(secret: VrfSecretKey) =>
                val public = secret.publicImage()
                ApiResponseUtil.toResponse(RespCreateVrfSecret(public))
              case Failure(e) =>
                ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create secret.", JOptional.of(e)))
            }
          }
        }
      }
    }
  }

  /**
   * Create new secret and return corresponding address (public key)
   */
  def createPrivateKey25519: Route = (post & path("createPrivateKey25519")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateKey]) { _ =>
          withNodeView { _ =>
            val secretFuture = sidechainNodeViewHolderRef ? ReceivableMessages.GenerateSecret(PrivateKey25519Creator.getInstance)
            Await.result(secretFuture, timeout.duration).asInstanceOf[Try[PrivateKey25519]] match {
              case Success(secret: PrivateKey25519) =>
                ApiResponseUtil.toResponse(RespCreatePrivateKey(secret.publicImage()))
              case Failure(e) =>
                ApiResponseUtil.toResponse(ErrorSecretNotAdded("Failed to create secret.", JOptional.of(e)))
            }
          }
        }
      }
    }
  }

  /**
    * Returns the list of all wallet’s propositions (public keys). Filter propositions of the given type
    */
  def allPublicKeys: Route = (post & path("allPublicKeys")) {
    withBasicAuth {
      _ => {
        entity(as[ReqAllPropositions]) { body =>
          withNodeView { sidechainNodeView =>
            val wallet = sidechainNodeView.getNodeWallet
            val optPropType = body.proptype
            if (optPropType.isEmpty) {
              val listOfPropositions = wallet.allSecrets().asScala.map(s =>
                s.publicImage().asInstanceOf[SidechainTypes#SCP])
              ApiResponseUtil.toResponse(RespAllPublicKeys(listOfPropositions))
            } else {

              getClassBySecretClassName(optPropType.get) match {
                case Failure(exception) => SidechainApiError(exception)
                case Success(clazz) =>
                  val listOfPropositions = wallet.secretsOfType(clazz).asScala.map(secret =>
                    secret.publicImage().asInstanceOf[SidechainTypes#SCP])
                  ApiResponseUtil.toResponse(RespAllPublicKeys(listOfPropositions))
              }
            }
          }
        }
      }
    }
  }

  def getClassBySecretClassName(className: String): Try[java.lang.Class[_ <: SidechainTypes#SCS]] = {
    Try(Class.forName(className).asSubclass(classOf[SidechainTypes#SCS])) orElse
      Try(Class.forName("io.horizen.secret." + className).asSubclass(classOf[SidechainTypes#SCS]))
  }

  /**
   * Import a private key inside the wallet
   */
  def importSecret: Route = (post & path("importSecret")) {
    withBasicAuth {
      _ => {
        entity(as[ReqImportSecret]) { body =>
          sidechainSecretsCompanion.parseBytesTry(BytesUtils.fromHexString(body.privKey)) match {
            case Success(secret) =>

              val future = sidechainNodeViewHolderRef ? LocallyGeneratedSecret(secret)
              Await.result(future, timeout.duration).asInstanceOf[Try[Unit]] match {
                case Success(_) =>
                  ApiResponseUtil.toResponse(RespCreatePrivateKey(secret.publicImage()))
                case Failure(e) =>
                  ApiResponseUtil.toResponse(ErrorSecretAlreadyPresent("Failed to add the key.", JOptional.of(e)))
              }

            case Failure(e) =>
              log.error(s"Import Wallet: Failed to parse secret", e)
              ApiResponseUtil.toResponse(ErrorFailedToParseSecret("ErrorFailedToParseSecret", JOptional.of(e)))

          }
        }
      }
    }
  }

  /**
   * Export a private key from the wallet based on its public key
   */
  def exportSecret: Route = (post & path("exportSecret")) {
    withBasicAuth {
      _ => {
        entity(as[ReqExportSecret]) { body =>
          withNodeView { sidechainNodeView =>
            val wallet = sidechainNodeView.getNodeWallet
            val optionalPrivKey: JOptional[Secret] = wallet.secretByPublicKeyBytes(BytesUtils.fromHexString(body.publickey))
            if (optionalPrivKey.isEmpty) {
              ApiResponseUtil.toResponse(ErrorPropositionNotFound("Proposition not found in the wallet!", JOptional.empty()))
            } else {
              ApiResponseUtil.toResponse(RespExportSecret(BytesUtils.toHexString(sidechainSecretsCompanion.toBytes(optionalPrivKey.get()))))
            }
          }
        }
      }
    }
  }

  /**
   * Perform a dump on a file of all the secrets inside the wallet.
   */
  def dumpSecrets: Route = (post & path("dumpSecrets")) {
    withBasicAuth {
      _ => {
        entity(as[ReqDumpSecrets]) { body =>
          val writer = new PrintWriter(new File(body.path))
          writer.write(s"# Secrets dump created on ${java.time.Instant.now()} \n")
          withNodeView { sidechainNodeView =>
            val wallet = sidechainNodeView.getNodeWallet
            wallet.allSecrets().forEach(key =>
              writer.write(BytesUtils.toHexString(sidechainSecretsCompanion.toBytes(key)) + " " + BytesUtils.toHexString(key.publicImage().bytes()) + "\n")
            )
            writer.close()
            ApiResponseUtil.toResponse(RespDumpSecrets(s"Secrets dump completed successfully at: ${body.path}"))
          }
        }
      }
    }
  }

  /**
   * Import all secrets contained in a file.
   * The file format should be equal to the file format generated by the endpoint dumpSecrets. (SECRETS + " " + PUBLICKEY)
   */
  def importSecrets: Route = (post & path("importSecrets")) {
    withBasicAuth {
      _ => {
        entity(as[ReqImportSecrets]) { body =>
          val reader = new Scanner(new File(body.path))

          //First collect every secrets and verify that their public image match with the corresponding public key in the file.
          var lineNumber = 1
          val secrets = new util.ArrayList[(SidechainTypes#SCS, Int)]()
          var error: JOptional[ErrorResponse] = JOptional.empty()
          while (reader.hasNextLine && error.isEmpty) {
            val line = reader.nextLine()
            if (!line.contains("#")) {
              val keyPair = line.split(" ")
              sidechainSecretsCompanion.parseBytesTry(BytesUtils.fromHexString(keyPair(0))) match {
                case Success(value) =>
                  if (!BytesUtils.toHexString(value.publicImage().bytes()).equals(keyPair(1))) {
                    log.error(s"Import Wallet: Public key doesn't match: ${BytesUtils.toHexString(value.publicImage().bytes())}  ${keyPair(1)}")
                    error = JOptional.of(ErrorPropositionNotMatch(s"Public key doesn't match on line $lineNumber", JOptional.empty()))
                  } else {
                    secrets.add((value, lineNumber))
                  }
                case Failure(e) =>
                  log.error(s"Import Wallet: Failed to parse the secret at line $lineNumber", e)
                  error = JOptional.of(ErrorFailedToParseSecret(s"Failed to parse the secret at line $lineNumber", JOptional.of(e)))
              }
            }
            lineNumber += 1
          }

          if (error.isPresent) {
            ApiResponseUtil.toResponse(error.get())
          } else {
            //Try to import the secrets
            var successfullyAdded = 0
            var failedToAdd = 0
            val errorDetail = new util.ArrayList[ImportSecretsDetail]()
            secrets.forEach(secret => {
              val future = sidechainNodeViewHolderRef ? LocallyGeneratedSecret(secret._1)
              Await.result(future, timeout.duration).asInstanceOf[Try[Unit]] match {
                case Success(_) =>
                  log.info("Import Wallet: Successfully added the proposition: " + BytesUtils.toHexString(secret._1.publicImage().bytes()))
                  successfullyAdded += 1
                case Failure(e) =>
                  log.error("Import Wallet: Failed to add the proposition: " + BytesUtils.toHexString(secret._1.publicImage().bytes()), e)
                  failedToAdd += 1
                  errorDetail.add(ImportSecretsDetail(secret._2, e.getMessage))
              }
            })
            ApiResponseUtil.toResponse(RespImportSecrets(successfullyAdded, failedToAdd, errorDetail))
          }
        }
      }
    }
  }

  override def listOfDisabledEndpoints(params: NetworkParams): Seq[(EndpointPrefix, EndpointPath, Option[ErrorMsg])] = {
    if (!params.isHandlingTransactionsEnabled) {
      val error = Some(ErrorNotEnabledOnSeederNode.description)
      Seq(
        (walletPathPrefix, "", error)
      )
    } else
      Seq.empty
  }


}

object WalletBaseRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  case class RespCreatePrivateKey(proposition: Proposition) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class RespCreateVrfSecret(proposition: VrfPublicKey) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class ReqAllPropositions(proptype: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class RespAllPublicKeys(propositions: Seq[Proposition]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  case class ReqCreateKey() {
  }
  @JsonView(Array(classOf[Views.Default]))
  case class ReqImportSecret(privKey: String) {
    require(privKey.nonEmpty, "Private key cannot be empty!")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class ReqExportSecret(publickey: String) {
    require(publickey.nonEmpty, "Publickey cannot be empty!")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class RespExportSecret(privKey: String)  extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  case class ReqImportSecrets(path: String) {
    require(path.nonEmpty, "Path cannot be empty!")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class RespImportSecrets(successfullyAdded: Int, failedToAdd: Int, summary: util.ArrayList[ImportSecretsDetail])  extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  case class ReqDumpSecrets(path: String) {
    require(path.nonEmpty, "Path cannot be empty!")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class RespDumpSecrets(status: String)  extends SuccessResponse

}

object WalletBaseErrorResponse {

  case class ErrorSecretNotAdded(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0301"
  }

  case class ErrorSecretAlreadyPresent(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0302"
  }

  case class ErrorPropositionNotFound(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0303"
  }

  case class ErrorPropositionNotMatch(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0304"
  }

  case class ErrorFailedToParseSecret(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0305"
  }
}


@JsonView(Array(classOf[Views.Default]))
case class ImportSecretsDetail private (lineNumber: Int,
                                        description: String) {
  def getLineNumber: Int = {
    lineNumber
  }

  def getDescription: String = {
    description
  }
}
