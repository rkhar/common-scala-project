package egress

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.math.BigDecimal
import scala.util.{Failure, Success}

object ZeebeHttpClient {
  abstract class ZeebeMessage
  case class StartDeleteProfileFlow(customerDTO: CustomerDTO, idToken: String) extends ZeebeMessage
  case class StartBlockProfileFlow(customerId: String, isBlocked: Boolean)     extends ZeebeMessage

  case class DepositOpenFlow(
      customerDTO: CustomerDTO,
      amount: BigDecimal
  ) extends ZeebeMessage

  case class DepositWithdrawalFlow(
      customerDTO: CustomerDTO,
      depositId: String,
      amount: BigDecimal
  ) extends ZeebeMessage

  case class DepositReplenishmentFlow(
      customerDTO: CustomerDTO,
      depositId: String,
      amount: BigDecimal
  ) extends ZeebeMessage

  case class DepositClosureFlow(
      customerDTO: CustomerDTO,
      depositId: String
  ) extends ZeebeMessage

  case class StartTopUpFlow(
      customerDTO: Option[CustomerDTO] = None,
      currency: String,
      amount: BigDecimal,
      topUpType: String,
      bankCredentials: Option[BankCredentials] = None,
      cryptoAddress: Option[CryptoAddress] = None
  ) extends ZeebeMessage

  case class StartCallbackTopUpFlow(
      customerDTO: Option[CustomerDTO] = None,
      currency: String,
      amount: BigDecimal,
      topUpType: String = "crypto",
      cryptoAddress: Option[CryptoAddress] = None,
      coinbaseCallBack: CoinbaseCallbackBody
  ) extends ZeebeMessage

  case class StartWithdrawFlow(
      customerDTO: Option[CustomerDTO] = None,
      currency: String,
      amount: BigDecimal,
      address: Option[String] = None,
      firstName: Option[String] = None,
      lastName: Option[String] = None,
      sepa: Option[Sepa] = None,
      swift: Option[Swift] = None,
      withdrawType: String
  ) extends ZeebeMessage

  case class StartExchangeFlow(
      customerDTO: Option[CustomerDTO] = None,
      sellCurrency: String,
      buyCurrency: String,
      amount: BigDecimal
  ) extends ZeebeMessage

  case class StartTransferFlow(
      customerDTO: CustomerDTO,
      customerId2: String,
      currency: String,
      amount: BigDecimal
  ) extends ZeebeMessage

  case class StartTransferFlowDTO(receiverCustomerId: String, currency: String, amount: BigDecimal) extends ZeebeMessage

  case class Location(
      projectName: String,
      className: String,
      methodName: String
  )

  case class StartErrorNotificationFlow(
      location: Location,
      errorInfo: String,
      status: String,
      failedWorkflowInstanceKey: Option[String] = None
  ) extends ZeebeMessage

  case class ProcessIds(
      deleteProfile: String,
      blockProfile: String,
      topup: String,
      coinbaseCallbackTopup: String,
      withdraw: String,
      exchange: String,
      transfer: String,
      depositOpen: String,
      depositWithdrawal: String,
      depositReplenishment: String,
      depositClosure: String,
      exceptionHandler: String
  )

  case class ZeebeWorkflowResponse(
      workflowInstanceKey: String,
      bpmnProcessId: String,
      version: String,
      workflowKey: String
  )

  case class CoinbaseCallbackWarning(
      `type`: Option[String],
      title: Option[String],
      details: Option[String],
      image_url: Option[String]
  )
  case class CoinbaseCallbackAddressInfo(address: Option[String])

  case class CoinbaseCallbackData(
      id: String,
      address: String,
      addressInfo: Option[CoinbaseCallbackAddressInfo],
      name: Option[String],
      created_at: Option[String],
      updated_at: Option[String],
      network: Option[String],
      uri_scheme: Option[String],
      resource: Option[String],
      resource_path: Option[String],
      warnings: Option[List[CoinbaseCallbackWarning]],
      deposit_uri: Option[String]
  )
  case class CoinbaseCallbackUser(id: Option[String], resource: Option[String], resource_path: Option[String])
  case class CoinbaseCallbackAccount(id: Option[String], resource: Option[String], resource_path: Option[String])
  case class CoinbaseCallbackAmount(amount: BigDecimal, currency: String)
  case class CoinbaseCallbackTransaction(id: Option[String], resource: Option[String], resource_path: Option[String])

  case class CoinbaseCallbackAdditionalDate(
      hash: Option[String],
      amount: CoinbaseCallbackAmount,
      transaction: Option[CoinbaseCallbackTransaction]
  )

  case class CoinbaseCallbackBody(
      id: String,
      `type`: String,
      data: CoinbaseCallbackData,
      user: Option[CoinbaseCallbackUser],
      account: Option[CoinbaseCallbackAccount],
      delivery_attempts: Option[Int],
      created_at: Option[String],
      resource: Option[String],
      resource_path: Option[String],
      additional_data: CoinbaseCallbackAdditionalDate
  )

}

class ZeebeHttpClient(
    processIds: ProcessIds,
    override val endpointKey: String = "zeebe",
    override val system: ActorSystem,
    elasticRepository: ElasticRepository,
    implicit override val executionContext: ExecutionContext
) extends AbstractHttpClient
    with CustomRejectionHandlers {

  private val logger = Logger(LoggerFactory.getLogger(this.getClass))
  implicit val formats: DefaultFormats.type = DefaultFormats
  val conf: Config                          = ConfigFactory.load()
  def getBaseUrl: String                    = endpointUrl + urlPrefix

  def getWorkflowData(
      workflowId: String
  ): Future[Either[ErrorInfo, IndexedSeq[CryptoOperation]]] = {
    logger.debug("got getWorkflowData with workflowId: {}", workflowId)
    val result = Promise[Either[ErrorInfo, IndexedSeq[CryptoOperation]]]
    elasticRepository.getCryptoOperationById(workflowId).onComplete {
      case Success(seq) =>
        result.success(Right(seq))
      case Failure(exception) =>
        result.failure(
          ServiceUnavailableException(ELASTIC_ERROR, Some(exception.toString))
        )
    }
    result.future
  }

  def startZeebeWorkflow(body: ZeebeMessage): Future[Either[ErrorInfo, Json]] = {

    val (processId, bankCred, errorInfo, zeebeBody) = body match {
      case cmd: StartTopUpFlow if cmd.topUpType.equals("bank") =>
        val cur = cmd.currency.toLowerCase()
        if (cur.equals("gbp") || cur.equals("usd") || cur.equals("eur")) {
          val bankCred = BankCredentials(
            name = conf.getString(s"bank.$cur.name"),
            iban = conf.getString(s"bank.$cur.iban"),
            bankName = conf.getString(s"bank.$cur.bankName"),
            swift = conf.getString(s"bank.$cur.swift"),
            bankAddress = conf.getString(s"bank.$cur.bankAddress"),
            sortCode = conf.optionalString(s"bank.$cur.sortCode"),
            accountNumber = conf.optionalString(s"bank.$cur.accountNumber")
          )
          (processIds.topup, Some(bankCred), None, cmd.copy(bankCredentials = Some(bankCred)))
        } else {
          (processIds.topup, None, Some(s"Wrong currency: ${cmd.currency}"), cmd)
        }
      case cmd: StartDeleteProfileFlow => (processIds.deleteProfile, None, None, cmd)
      case cmd: StartBlockProfileFlow  => (processIds.blockProfile, None, None, cmd)
      case cmd: StartTopUpFlow => (processIds.topup, None, None, cmd)
      case cmd: StartCallbackTopUpFlow => (processIds.coinbaseCallbackTopup, None, None, cmd)
      case cmd: StartWithdrawFlow => (processIds.withdraw, None, None,  cmd)
      case cmd: StartExchangeFlow => (processIds.exchange, None, None, cmd)
      case cmd: StartTransferFlow => (processIds.transfer, None, None, cmd)
      case cmd: StartErrorNotificationFlow => (processIds.exceptionHandler, None, None, cmd)
      case cmd                             => (processIds.topup, None, Some(s"Operation Not Supported"), cmd)
    }

    logger.debug(s"ZeebeBody is: $zeebeBody" )

    val message = HttpRequest(
      method = HttpMethods.POST,
      uri = getBaseUrl + "/start/workflow",
      entity = HttpEntity(ContentTypes.`application/json`, write(zeebeBody))
    ).withHeaders(
      RawHeader("accept", "*/*"),
      RawHeader("processId", processId)
    )
    if (errorInfo.isDefined) finishWithErrorInfo(BadRequestException(FintelErrorCodes.WALLET_ERROR, errorInfo))
    else send(message, bankCred)
  }

  def processCoinbaseCallback(callback: CoinbaseCallbackBody): Future[Either[ErrorInfo, Json]] = {
    logger.debug("Got callback from coinbase: {}", callback)
    val result = Promise[Either[ErrorInfo, Json]]

    callback.`type` match {
      case "wallet:addresses:new-payment" =>
        elasticRepository.getCryptoOperationByCoinbaseAddress(callback.data.address).onComplete {
          case Success(seq) =>
            if (seq.isEmpty) {
              result.failure(
                NotFoundException(ELASTIC_ERROR, Some(s"Not found cryptoOperation by address: $callback"))
              )
              val message = s"Not found crypto operation by address ${callback.id}"
              startZeebeWorkflow(
                StartErrorNotificationFlow(
                  Location("main-api", "ZeebeHttpClient", "processCoinbaseCallback"),
                  message,
                  "500"
                )
              )
            } else {
              if (!seq.last.srcCurrency.get.equals(callback.additional_data.amount.currency)) {
                result.failure(
                  ServiceUnavailableException(
                    ELASTIC_ERROR,
                    Some(s"srcCurrency of cryptoOperation not equal to callback currency: ${callback.data.address}")
                  )
                )
                val message = s"srcCurrency of cryptoOperation not equal to callback currency: ${callback.data.address} -> " +
                      s"operationId: ${seq.last.operationId} srcCurrency: ${seq.last.srcCurrency}"
                startZeebeWorkflow(
                  StartErrorNotificationFlow(
                    Location("main-api", "ZeebeHttpClient", "processCoinbaseCallback"),
                    message,
                    "500"
                  )
                )
              } else if (seq.last.state.equals("HOLD") && seq.last.srcCurrency.get
                           .equals(callback.additional_data.amount.currency)) {
                val message = HttpRequest(
                  method = HttpMethods.POST,
                  uri = getBaseUrl + "/complete/task",
                  entity = HttpEntity(ContentTypes.`application/json`, Map("callback" -> callback).asJson.noSpaces)
                ).withHeaders(
                  RawHeader("accept", "*/*"),
                  RawHeader("correlationId", seq.last.operationId),
                  RawHeader("messageName", "Message_COINBASE_TOPUP_CALLBACK")
                )
                send(message).map { x =>
                  result.success(x)
                }
              } else {
                startZeebeWorkflow(
                  StartCallbackTopUpFlow(
                    customerDTO = Some(seq.last.customerDTO),
                    currency = callback.additional_data.amount.currency,
                    amount = callback.additional_data.amount.amount,
                    cryptoAddress =
                      Some(CryptoAddress(address = callback.data.address, addressId = Some(callback.data.id))),
                    coinbaseCallBack = callback
                  )
                ).map { x =>
                  result.success(x)
                }
              }
            }
          case Failure(exception) =>
            result.failure(
              ServiceUnavailableException(ELASTIC_ERROR, Some(exception.toString))
            )
        }
        result.future
      case any =>
        finishWithErrorInfo(ConflictException(FintelErrorCodes.REDIS_ERROR, Some(s"type $any not supported yet")))
    }
  }

  def send(message: HttpRequest, bankCred: Option[BankCredentials] = None): Future[Either[ErrorInfo, Json]] = {
    logger.debug("send msg: {}", message)
    val result = Promise[Either[ErrorInfo, Json]]

    processRequest(
      message
    ).onComplete {
      case Success(response) =>

        response match {
          case Right(value) =>
            value.as[ZeebeWorkflowResponse] match {
              case Right(v) =>
                logger.debug("got response from zeebe-receive-api: {}", v)
                if (bankCred.isDefined)
                  result.success(Right(BankTopUpResponse(bankCred.get, v.workflowInstanceKey).asJson))
                else
                  result.success(Right(StartWorkflowResponse(v.workflowInstanceKey, v.bpmnProcessId, v.version).asJson))
              case Left(v) =>
                logger.warn(v.message)
                result.success(Right(value))
            }

          case Left(value) =>
            result.success(Left(value))
        }
      case Failure(ex) =>
        result.success(prepareErrorInfo(FintelErrorCodes.ZEEBE_ERROR, s"got response Failure: $ex"))
    }
    result.future
  }

  def prepareErrorInfo(errorCode: ErrorCode, message: String): Either[ErrorInfo, Json] = {
    logger.error(message)
    Left(
      exceptionToErrorInfo(
        ConflictException(errorCode, Some(message))
      )
    )
  }

  def finishWithErrorInfo(apiEx: ApiException): Future[Either[ErrorInfo, Json]] = {
    logger.error(apiEx.getMessage)
    Future.successful(
      Left(
        exceptionToErrorInfo(
          apiEx
        )
      )
    )
  }

}
