package com.horizen.api.http

/*One case class for each request, whose signature reflects the parameters we expect in the JSON input of the corresponding API Call.
* Also, basic preliminary checks are performed.*/

import io.circe.ParsingFailure
import io.circe
import io.circe.{Decoder, DecodingFailure, parser}

case class GetClosedBoxesRequest(exclude: Option[List[String]])

case class GetClosedBoxesOfTypeRequest(boxtype: String, exclude: Option[List[String]])

case class GetBalanceByTypeRequest(balancetype: String)

case class GetPublicKeysPropositionsByTypeRequest(proptype: String)


import cats.data.{NonEmptyList, Validated}

import scala.util.{Failure, Success, Try}

object ApiInputParser{

  //Wrapper: decode the body string as json conforming to the case class T. This allows to handle none inputs and better handle exceptions
  def parseInput[T](body: String)(implicit decoder: Decoder[T]): Try[T] = {
    var toDecode: String = body

    //Needed for APIs with all parameters optional. In other cases it will just throw an error later
    if(body.isEmpty)
      toDecode = "{}"

    //Decode JSON and apply it to case class
    parser.decodeAccumulating[T](toDecode) match {
      case Validated.Invalid(e) => Failure(readException(e))
      case Validated.Valid(succ) => Success(succ)

    }
  }

  //Convert the exception thrown by the decoder into a more user readable format
  private def readException(errors: NonEmptyList[circe.Error]): Exception = {
    var parsingErrors: String = ""
    var missingErrors: String = ""
    var wrongValueErrors: String = ""

    for(e <- errors.toList) {
      e match {
        case parsingError: ParsingFailure => parsingErrors = parsingErrors + parsingError.getMessage() + " "
        case decodingError: DecodingFailure =>

          //Missing mandatory parameter
          if (decodingError.getMessage().contains("Attempt to decode value on failed cursor"))
            decodingError.history.foreach(ops => missingErrors = missingErrors + ops.productIterator.next().toString + " ")

          //Wrong type parameter
          else
            decodingError.history.foreach(ops => wrongValueErrors = wrongValueErrors + ops.productIterator.next().toString + " ")
      }
    }

    //Build error strings
    if(!parsingErrors.isEmpty)
      new Exception("Parsing error(s): " + parsingErrors)
    else {

      if (!missingErrors.isEmpty)
        missingErrors = "Missing required parameter(s): " + missingErrors

      if (!wrongValueErrors.isEmpty)
        wrongValueErrors = "Wrong value for parameter(s): " + wrongValueErrors

      new Exception(missingErrors + "\n" + wrongValueErrors)
    }
  }
}
