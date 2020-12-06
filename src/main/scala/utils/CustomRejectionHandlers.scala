package utils

import java.util.Locale
import scala.concurrent.Future

trait CustomRejectionHandlers {

  protected val log: Logger = LoggerFactory.getLogger(getClass)

  val errorSeries: FintelApiErrorSeries.type = ErrorConfig.errorSeries
  val errorSystem: FintelErrorSystem.type    = ErrorConfig.errorSystem

  val timeoutHandler: HttpRequest => HttpResponse = { r =>
    HttpResponse(
      status = akka.http.scaladsl.model.StatusCodes.ServiceUnavailable,
      entity = HttpEntity(
        contentType = ContentTypes.`application/json`,
        data = ByteString.fromArray(
          HttpUtil
            .apiExceptionToErrorInfo(
              ServerErrorRequestException(
                DarErrorCodes.TIMEOUT_ERROR(errorSeries, errorSystem)
              ),
              lang = HttpUtil.getLanguageFromHeader(HttpUtil.getHeaders(r.headers))
            )
            .asJson
            .noSpaces
            .getBytes("UTF-8")
        )
      )
    )
  }

  def completeWithError(apiException: ApiException): RequestContext => Future[RouteResult] = {
    context: RequestContext =>
      val language = HttpUtil.getLanguageFromRequestContext(context)
      val stan     = "NO_STAN"
      val errorInfo: ErrorInfo =
        apiException.getErrorInfo(ErrorLocaleContextFactory.getContextForLocale(new Locale(language)))

      if(apiException.status.intValue >= 500 && apiException.status.intValue < 600) {
        log.error(
          "[{}] Application return expected error status code [{}] {} with entity {}",
          stan,
          language,
          apiException.status,
          errorInfo
        )
      } else {
        log.debug(
          "[{}] Application return expected error status code [{}] {} with entity {}",
          stan,
          language,
          apiException.status,
          errorInfo
        )
      }

      context.complete((StatusCode.int2StatusCode(apiException.status.intValue), errorInfo))
  }

  implicit def customRejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case AuthenticationFailedRejection(cause, challengeHeaders) =>
          val rejectionMessage = cause match {
            case CredentialsMissing  => "The resource requires authentication, which was not supplied with the request"
            case CredentialsRejected => "The supplied authentication is invalid"
          }
          completeWithError(
            UnauthorizedErrorException(
              DarErrorCodes.AUTHENTICATION_FAILED_REJECTION(errorSeries, errorSystem),
              Some(rejectionMessage)
            )
          )
      }
      .handle {
        case AuthorizationFailedRejection =>
          completeWithError(
            ForbiddenErrorException(
              DarErrorCodes.AUTHORIZATION_FAILED_REJECTION(errorSeries, errorSystem),
              Some("The supplied authentication is not authorized to access this resource")
            )
          )
      }
      .handle {
        case MalformedFormFieldRejection(name, msg, _) =>
          completeWithError(
            BadRequestException(DarErrorCodes.MALFORMED_FORM_FIELD_REJECTION(errorSeries, errorSystem), Some(msg))
          )
      }
      .handle {
        case MalformedQueryParamRejection(name, msg, _) =>
          completeWithError(
            BadRequestException(DarErrorCodes.MALFORMED_QUERY_PARAM_REJECTION(errorSeries, errorSystem), Some(msg))
          )
      }
      .handle {
        case MalformedRequestContentRejection(msg, _) =>
          completeWithError(
            BadRequestException(
              DarErrorCodes.MALFORMED_REQUEST_CONTENT_REJECTION(errorSeries, errorSystem),
              Some(msg)
            )
          )
      }
      .handle {
        case MissingFormFieldRejection(fieldName) =>
          completeWithError(
            BadRequestException(
              DarErrorCodes.MISSING_FORM_FIELD_REJECTION(errorSeries, errorSystem),
              Some("Missing field name: " + fieldName)
            )
          )
      }
      .handle {
        case MissingHeaderRejection(headerName) =>
          completeWithError(
            BadRequestException(
              DarErrorCodes.MISSING_HEADER_REJECTION(errorSeries, errorSystem),
              Some("Missing header name: " + headerName)
            )
          )
      }
      .handle {
        case MissingQueryParamRejection(paramName) =>
          completeWithError(
            NotFoundException(
              DarErrorCodes.MISSING_QUERY_PARAM_REJECTION(errorSeries, errorSystem),
              Some("Missing param name: " + paramName)
            )
          )
      }
      .handle {
        case RequestEntityExpectedRejection =>
          completeWithError(
            BadRequestException(
              DarErrorCodes.REQUEST_ENTITY_EXPECTED_REJECTION(errorSeries, errorSystem),
              Some("Request entity expected")
            )
          )
      }
      .handle {
        case ValidationRejection(msg, _) =>
          completeWithError(
            BadRequestException(DarErrorCodes.MALFORMED_QUERY_PARAM_REJECTION(errorSeries, errorSystem), Some(msg))
          )
      }
      .handle {
        case MethodRejection(supportedMethod) =>
          completeWithError(
            MethodNotAllowedException(
              DarErrorCodes.METHOD_NOT_ALLOWED_REJECTION(errorSeries, errorSystem),
              Some("Method not allowed. Supported method is " + supportedMethod.value)
            )
          )
      }
      .handleNotFound {
        completeWithError(
          NotFoundException(
            DarErrorCodes.REQUESTED_RESOURCE_NOT_FOUND(errorSeries, errorSystem),
            Some("Whoops, requested resource is not found")
          )
        )
      }
      .result()

  implicit def customExceptionHandler: ExceptionHandler = ExceptionHandler {

    case ex: ApiException =>
      completeWithError(ex)

    case ex: RuntimeException =>
      ex.printStackTrace()

      completeWithError(
        ServerErrorRequestException(
          DarErrorCodes.INTERNAL_SERVER_ERROR(errorSeries, errorSystem),
          Some(
            "There was an internal server error. Please, retry your attempt later or contact to the software support center."
          )
        )
      )

  }

}
