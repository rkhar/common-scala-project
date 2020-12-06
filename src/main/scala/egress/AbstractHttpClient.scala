package egress

import java.math.BigInteger
import java.security.MessageDigest
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait AbstractHttpClient {

  def endpointKey: String

  implicit def system: ActorSystem

  implicit def executionContext: ExecutionContext

  val config: Config = system.settings.config.getConfig(s"endpoints.$endpointKey")

  val queueSize: Int = config.getInt("queue-size")

  val endpointUrl: String = config.getString("url")

  val urlPrefix: String = Try { config.getString("prefix") }.getOrElse("")

  protected def log: Logger

  protected val poolClientFlow
      : Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), HostConnectionPool] = {

    val uri = Uri(endpointUrl)

    if (uri.scheme.equals("http")) {

      Http().cachedHostConnectionPool[Promise[HttpResponse]](
        host = uri.authority.host.toString(),
        port = uri.effectivePort
      )

    } else {

      Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]](
        host = uri.authority.host.toString(),
        port = uri.effectivePort
      )

    }
  }

  protected val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
    .queue[(HttpRequest, Promise[HttpResponse])](queueSize, OverflowStrategy.dropNew)
    .via(poolClientFlow)
    .to(Sink.foreach({
      case (Success(resp), p) => p.success(resp)
      case (Failure(e), p)    => p.failure(e)
    }))
    .run()

  protected def queueRequest(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued    => responsePromise.future
      case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future
          .failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
    }
  }

  protected def getErrorInfo(body: String): Either[Throwable, ErrorInfo] =
    decode[ErrorInfo](body)

  protected def processRequest(request: HttpRequest, tryParseErrorInfo: Boolean = true): Future[Either[ErrorInfo, Json]] = {

    val result = Promise[Either[ErrorInfo, Json]]

    log.debug(s"[1] Request uri path: {} and uri {}", request.uri.path, request.uri.toString())

    queueRequest(request).onComplete {
      case Success(response) =>

        Unmarshal(response.entity).to[String].onComplete {

          case Success(jsonString) if response.status.intValue() >= 200 && response.status.intValue < 300 =>

            log.debug(s"[2] Response is with status: {} -> {}", response.status, jsonString)

            if (jsonString.trim.isEmpty) {
              result.success(Right(Json.Null))
            } else {

              parse(jsonString) match {

                case Right(r) => result.success(Right(r))

                case Left(ex) =>

                  log.error("[3] InternalServerError: Failed to parse and extract response body: {}",
                    ex.getMessage)

                  result.failure(ex)

              }

            }

          case Success(jsonString) =>

            if(response.status.intValue() >= 500 && response.status.intValue() < 600) {
              log.error("Got error from service: uri={} | response={}",
                request.uri.path, jsonString)
            }

            if(tryParseErrorInfo) {

              getErrorInfo(jsonString) match {
                case Right(errorInfo) =>

                  log.debug("[5] Got error info: {}", errorInfo)
                  result.success(Left(errorInfo))

                case Left(ex) =>

                  log.error("[6] BadGateway: Failed to parse ErrorInfo: {} with jsonString: {}",
                    ex.getMessage, jsonString)

                  result.failure(
                    ServiceUnavailableException(
                      DarErrorCodes.JSON_PARSE_ERROR(ErrorConfig.errorSeries, ErrorConfig.errorSystem),
                      Some(jsonString)
                    )
                  )
              }

            } else {

              log.debug("[7] Error request body from service: {}", jsonString)

              result.failure(
                ServiceUnavailableException(
                  DarErrorCodes.JSON_PARSE_ERROR(ErrorConfig.errorSeries, ErrorConfig.errorSystem),
                  Some(jsonString)
                )
              )

            }

          case Failure(ex) =>

            log.error("[8] InternalServerError: Failed to unmarshal response body to string: {}",
              ex.getMessage)

            result.failure(ex)

        }

      case Failure(ex) =>

        log.error("[9] BadGateway: Failed to execute request | ex={}",
          ex.getMessage)

        result.failure(ex)

    }

    result.future

  }

  def getFullCode(errorCode: ErrorCode, status: StatusCode): String =
    errorCode.system.system + "." + ((status.intValue * 100 + errorCode.series.series) * 1000 + errorCode.code).toString

  def exceptionToErrorInfo(ex: ApiException): ErrorInfo = {
    val fullCode: String = getFullCode(ex.errorCode, ex.status)
    val errorUrl: String = "docs/" + fullCode
    val localizedMessage: Option[String] = ex.message match {
      case Some(message) => Some(message)
      case _             => None
    }

    ErrorInfo(
      Some(ex.errorCode.system.system),
      Some(ex.status.intValue),
      Some(ex.errorCode.series.series),
      Some(fullCode),
      localizedMessage,
      Some(ex.message.getOrElse("")),
      Some(errorUrl)
    )
  }

  def validateEmail(prefix: String): String = {

    val secretKey = config.getString("hash.secretKey")
    val body      = s"$prefix$secretKey"
    val hash =
      String.format("%032x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(body.getBytes("UTF-8"))))

    log.info(s"prefix: $prefix, hash: $hash")

    hash

  }

}
