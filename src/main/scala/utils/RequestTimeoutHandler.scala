package utils

trait RequestTimeoutHandler {

  val errorSeries: FintelApiErrorSeries.type = ErrorConfig.errorSeries
  val errorSystem: FintelErrorSystem.type    = ErrorConfig.errorSystem

  /**
    * Custom timeout handler (it's completely different from rejections ¯\_(ツ)_/¯)
    */
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

}
