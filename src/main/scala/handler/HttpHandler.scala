package handler

import java.util.Locale

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.Json
import kz.dar.eco.common.exception._
import kz.dar.eco.common.exception.localization.ErrorLocaleContextFactory
import kz.dar.fintel.customer.core.domain.DomainEntity
import kz.dar.fintel.customer.core.exception.ErrorConfig.{errorSeries, errorSystem}
import kz.dar.fintel.customer.core.util.{Codec, HttpUtil}
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait HttpHandler extends Codec {

  implicit def executionContext: ExecutionContext

  implicit def log: Logger

  def complete(future: Future[Any])(implicit context: RequestContext): Future[RouteResult] = {
    val p = Promise[RouteResult]

    future.onComplete {
      case Success(result)    => complete(result).map(v => p.complete(Try(v)))
      case Failure(exception) => complete(exception).map(v => p.complete(Try(v)))
    }

    p.future
  }

  def complete(any: Any)(implicit context: RequestContext): Future[RouteResult] =
    any match {
      case v: DomainEntity      => complete(StatusCodes.OK, v)
      case v: Seq[DomainEntity] => complete(StatusCodes.OK, v)
      case v: String            => complete(StatusCodes.OK, v)
      case v: Json              => complete(StatusCodes.OK, v)
      case e: ApiException      => completeWithError(e)
      case e: Throwable =>
        completeWithError(
          ServerErrorRequestException(DarErrorCodes.INTERNAL_SERVER_ERROR(errorSeries, errorSystem), Some(e.getMessage))
        )
      case a =>
        completeWithError(
          ServerErrorRequestException(
            DarErrorCodes.INTERNAL_SERVER_ERROR(errorSeries, errorSystem),
            Some(s"Unhandled response: $a")
          )
        )
    }

  def completeWithError(apiException: ApiException)(implicit context: RequestContext): Future[RouteResult] = {
    val language = HttpUtil.getLanguageFromRequestContext(context)
    val errorInfo: ErrorInfo =
      apiException.getErrorInfo(ErrorLocaleContextFactory.getContextForLocale(new Locale(language)))

    if (apiException.status.intValue >= 500 && apiException.status.intValue < 600) {
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

    complete(StatusCode.int2StatusCode(apiException.status.intValue), errorInfo)
  }

  def stan: Long = System.nanoTime()

  def complete(status: StatusCode, any: => ToResponseMarshallable)(
      implicit context: RequestContext
  ): Future[RouteResult] =
    context.complete(any).map {
      case successRouteResult: Complete =>
        successRouteResult.copy(response = successRouteResult.response.copy(status = status))
      case routeResult => routeResult
    }

}
