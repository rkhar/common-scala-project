package utils

import java.util.{Locale, UUID}
import scala.util.Random

object HttpUtil {

  val defaultLanguage = "en"

  /**
    * getLocalizedLanguage - choose possible language
    *
    * @param headerLanguage - concrete or default language
    * @return
    */
  def getLocalizedLanguage(headerLanguage: String): String =
    if (LocalizedMessages.locales.contains(headerLanguage)) headerLanguage else defaultLanguage

  /**
    * Throwable to ErrorInfo
    *
    * @param e - Exception
    */
  def throwableToErrorInfo(
      e: Throwable,
      lang: String,
      errorSeries: ErrorSeries,
      errorSystem: ErrorSystem
  ): ErrorInfo = {
    val apiException =
      ServerErrorRequestException(DarErrorCodes.INTERNAL_SERVER_ERROR(errorSeries, errorSystem), Some(e.getMessage))
    apiException.getErrorInfo(ErrorLocaleContextFactory.getContextForLocale(new Locale(lang)))
  }

  /**
    * ApiException to ErrorInfo
    *
    * @param apiException - ApiException
    */
  def apiExceptionToErrorInfo(apiException: ApiException, lang: String): ErrorInfo =
    apiException.getErrorInfo(ErrorLocaleContextFactory.getContextForLocale(new Locale(lang)))

  def prepareHeaders(headers: Option[Map[String, String]]): Seq[HttpHeader] = headers match {
    case Some(headersMap) =>
      headersMap.map(e => RawHeader(e._1, e._2)).toSeq :+ RawHeader("Accept-Language", "en")
    case _ =>
      Seq(RawHeader("Accept-Language", "en"))
  }

  private val numbers  = "0123456789"
  private val literals = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ(){}[]!@#$%&?=~*"

  def createRandomPassword(length: Int = 8, `type`: Int = 0): String = {
    var password = ""
    var i        = 0
    val pattern  = if (`type` == 0) numbers else literals

    while (i < length) {
      password += pattern.charAt(Random.nextInt(pattern.length))
      i += 1
    }
    password
  }

  def getLanguageFromRequestContext(context: RequestContext): String =
    context.request.headers.find(_.name.equalsIgnoreCase("Accept-Language")) match {
      case Some(header) =>
        val lan: Option[String] = header match {
          case `Accept-Language`(languages) =>
            languages.find(_.primaryTag != "null").map(_.primaryTag)

          case _ =>
            LocalizedMessages.locales.find(header.value.indexOf(_) >= 0)
        }

        lan match {
          case Some(l) => l
          case _       => defaultLanguage
        }

      case _ => defaultLanguage
    }

  def getLanguageFromHeader(header: Map[String, String]): String =
    header.find(_._1.equalsIgnoreCase("Accept-Language")) match {
      case Some(lh) =>
        LocalizedMessages.locales.find(lh._2.indexOf(_) >= 0) match {
          case Some(l) => l
          case _       => defaultLanguage
        }

      case _ => defaultLanguage
    }

  def getLanguageHeader(context: RequestContext): Option[HttpHeader] =
    context.request.headers.find(_.name.equalsIgnoreCase("Accept-Language"))

  def routesGathering(route: Seq[Route]): Route =
    route.foldLeft[Route](reject)(_ ~ _)

  def getHeaders(headers: Seq[HttpHeader]): Map[String, String] = {
    var hasLanguage = false
    var hasStan     = false

    val result: Map[String, String] = headers.map { h =>
      h.lowercaseName match {
        case "accept-language" =>
          hasLanguage = true
          (h.name, findLanguage(Some(h.value)))
        case "stan" =>
          hasStan = true
          (h.name, h.value)
        case _ => (h.name, h.value)
      }
    }.toMap

    /*
     * If language is not provided by customer,
     * then using default
     */
    val withLang = if (!hasLanguage) {
      result + ("Accept-Language" -> findLanguage(None))
    } else {
      result
    }

    /*
     * If stan is not provided by customer,
     * them generating a new one.
     */
    val withStan = if (!hasStan) {
      withLang + ("stan" -> UUID.randomUUID().toString)
    } else {
      withLang
    }

    /**
      * For now accepting only `Accept-Language` and `stan` to reduce traffic
      * In order to support more headers, just remove this filter or add
      * required headers to the list
      */
    val supportedHeaders = Set(
      "stan",
      "Accept-Language"
    )

    withStan.filter(i => supportedHeaders.contains(i._1))

  }

  def findLanguage(language: Option[String]): String = language match {
    case Some(l) => LocalizedMessages.locales.find(l.indexOf(_) >= 0).getOrElse(defaultLanguage)
    case _       => defaultLanguage
  }
}
