package egress

import java.io.File
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object CustomerHttpClient {

  case class CreateCustomerRequestBody(
      ttmCustomerId: Option[String] = None,
      sumsubCustomerId: Option[String] = None,
      phone: Option[String] = None,
      fullName: Option[String] = None,
      age: Option[Int] = None,
      email: Option[String] = None,
      isBlocked: Boolean = false,
      status: Option[String] = None,
      isEmailVerified: Boolean = false,
      isSumsubVerified: Boolean = false,
      isFullyVerified: Boolean = false
  )

  case class SumsubBody(externalUserId: String)
}

class CustomerHttpClient(
    redisRepository: RepositoryForRedis,
    override val endpointKey: String = "customer",
    override val system: ActorSystem,
    implicit override val executionContext: ExecutionContext
) extends AbstractHttpClient {

  import CustomerHttpClient._

  override protected val log: Logger = LoggerFactory.getLogger(getClass)

  def getBaseUrl: String = endpointUrl + urlPrefix + "/v1"

  def get(body: GetCustomerDTO): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(getBaseUrl + "/customers"),
        entity = HttpEntity(ContentTypes.`application/json`, body.asJson.dropNullValues.noSpaces)
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.CUSTOMER_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future

  }

  def getByPhoneNumbers(phones: List[String]): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(getBaseUrl + "/customers/phoneNumber"),
        entity = HttpEntity(ContentTypes.`application/json`, phones.asJson.noSpaces)
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.CUSTOMER_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future

  }

  def create(customerDTO: CreateCustomerRequestBody): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"$getBaseUrl/customers"),
        entity = HttpEntity(ContentTypes.`application/json`, customerDTO.asJson.noSpaces)
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.CUSTOMER_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future

  }

  def verifyCustomerByEmailWithLink(
      ttmCustomerId: String,
      email: String,
      hash: String
  ): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    if (validateEmail(s"$ttmCustomerId$email").equals(hash)) {

      processRequest(
        HttpRequest(
          method = HttpMethods.PUT,
          uri = Uri(s"$getBaseUrl/customers/verify/email").withQuery(
            Query(
              Map("ttmCustomerId" -> s"$ttmCustomerId", "email" -> s"$email")
            )
          )
        )
      ).onComplete {
        case Success(response) =>
          result.success(response)
        case Failure(ex) =>
          result.failure(
            BadRequestException(
              FintelErrorCodes.CUSTOMER_ERROR,
              Some("Exception message: " + ex.getMessage)
            )
          )
      }
    } else {
      result.failure(
        ConflictException(
          FintelErrorCodes.CUSTOMER_ERROR,
          Some("Exception message: wrong email")
        )
      )
    }

    result.future

  }

  def verifyCustomerBySumsub(ttmCustomerId: String): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      HttpRequest(
        method = HttpMethods.PUT,
        uri = Uri(s"$getBaseUrl/customers/verify/sumsub").withQuery(
          Query(Map("ttmCustomerId" -> s"$ttmCustomerId"))
        )
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.CUSTOMER_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future

  }

  def verifyCustomerByEmailWithOtp(
      ttmCustomerId: String,
      email: String,
      otp: String
  ): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    if (redisRepository.get(ttmCustomerId).contains(otp)) {
      processRequest(
        HttpRequest(
          method = HttpMethods.PUT,
          uri = Uri(s"$getBaseUrl/customers/verify/email").withQuery(
            Query(
              Map("ttmCustomerId" -> s"$ttmCustomerId", "email" -> s"$email")
            )
          )
        )
      ).onComplete {
        case Success(response) =>
          result.success(response)
        case Failure(ex) =>
          result.failure(
            ServiceUnavailableException(
              FintelErrorCodes.CUSTOMER_ERROR,
              Some("Exception message: " + ex.getMessage)
            )
          )
      }
    } else {
      result.failure(
        ConflictException(
          FintelErrorCodes.CUSTOMER_ERROR,
          Some("Exception message: OTP doesn't match")
        )
      )
    }

    result.future

  }

  def addDocument(
      customerId: String,
      docType: String,
      docValue: String,
      file: File
  ): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    val multipartFormEntity = FormData(
      Multipart.FormData.BodyPart.fromFile("file", ContentType.Binary(MediaTypes.`multipart/form-data`), file)
    ).toEntity()

    processRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"$getBaseUrl/sumsub/$customerId?docType=" + "\"" + docType + "\"&docValue=\"" + docValue + "\""),
        entity = multipartFormEntity
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.SUMSUB_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future
  }

  def getApplicant(
      customerId: String
  ): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"$getBaseUrl/sumsub/$customerId")
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.SUMSUB_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future
  }

  def getApplicantToken(
      ttmCustomerId: String
  ): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"$getBaseUrl/customers/sumsub/token/$ttmCustomerId")
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.SUMSUB_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future
  }

  def checkEmail(customerId: String, newEmail: String, oldEmail: String): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"$getBaseUrl/customers/$customerId/email/check").withQuery(
          Query(
            Map(
              "newEmail" -> s"$newEmail",
              "oldEmail" -> s"$oldEmail"
            )
          )
        )
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.SUMSUB_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future
  }

  def startUpdateEmail(
      customerId: String,
      newEmail: String,
      oldEmail: String,
      notificationType: String
  ): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"$getBaseUrl/customers/$customerId/email/update/start").withQuery(
          Query(
            Map(
              "newEmail"         -> s"$newEmail",
              "oldEmail"         -> s"$oldEmail",
              "notificationType" -> s"$notificationType"
            )
          )
        )
      )
    ).onComplete {
      case Success(response) =>
        result.success(response)
      case Failure(ex) =>
        result.failure(
          ServiceUnavailableException(
            FintelErrorCodes.SUMSUB_ERROR,
            Some("Exception message: " + ex.getMessage)
          )
        )
    }

    result.future
  }

  def updateEmailWithLink(
      customerId: String,
      newEmail: String,
      oldEmail: String,
      hash: String
  ): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    if (validateEmail(s"$customerId$newEmail$oldEmail").equals(hash)) {

      processRequest(
        HttpRequest(
          method = HttpMethods.PUT,
          uri = Uri(s"$getBaseUrl/customers/$customerId/email/update").withQuery(
            Query(
              Map("newEmail" -> s"$newEmail", "oldEmail" -> s"$oldEmail")
            )
          )
        )
      ).onComplete {
        case Success(response) =>
          result.success(response)
        case Failure(ex) =>
          result.failure(
            ServiceUnavailableException(
              FintelErrorCodes.CUSTOMER_ERROR,
              Some("Exception message: " + ex.getMessage)
            )
          )
      }
    } else {
      result.failure(
        ConflictException(
          FintelErrorCodes.CUSTOMER_ERROR,
          Some("Exception message: wrong email")
        )
      )
    }

    result.future

  }

  def updateEmailWithOtp(
      customerId: String,
      newEmail: String,
      oldEmail: String,
      otp: String
  ): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    if (redisRepository.get(customerId).contains(otp)) {
      processRequest(
        HttpRequest(
          method = HttpMethods.PUT,
          uri = Uri(s"$getBaseUrl/customers/$customerId/email/update").withQuery(
            Query(
              Map("newEmail" -> s"$newEmail", "oldEmail" -> s"$oldEmail")
            )
          )
        )
      ).onComplete {
        case Success(response) =>
          result.success(response)
        case Failure(ex) =>
          result.failure(
            ServiceUnavailableException(
              FintelErrorCodes.CUSTOMER_ERROR,
              Some("Exception message: " + ex.getMessage)
            )
          )
      }
    } else {
      result.failure(
        ConflictException(
          FintelErrorCodes.CUSTOMER_ERROR,
          Some("Exception message: OTP doesn't match")
        )
      )
    }

    result.future

  }

}
