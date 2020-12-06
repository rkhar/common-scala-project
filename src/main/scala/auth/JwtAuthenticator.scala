package auth

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{complete, optionalHeaderValueByName}
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.parser.decode
import kz.dar.fintel.main.api.utils.Codec
import io.circe.generic.auto._
import kz.dar.eco.common.exception.UnauthorizedErrorException
import kz.dar.fintel.main.api.egress.CustomerHttpClient
import kz.dar.fintel.main.api.exceptions.FintelErrorCodes.AUTH_API_ERROR
import kz.dar.fintel.main.api.model.GetCustomerDTO
import kz.dar.fintel.main.api.model.CustomerDTO
import org.json4s.native.JsonMethods.parse
import org.slf4j.{Logger, LoggerFactory}
import pdi.jwt.{Jwt, JwtAlgorithm}

import scala.util.{Failure, Success}

object JwtAuthenticator extends Codec {
  val log: Logger          = LoggerFactory.getLogger(getClass)
  val config: Config       = ConfigFactory.load()
  val whitelistIds: String = config.getString("auth.whitelist-authorization.ids")

  def authenticated(): Directive1[Map[String, Any]] = {
    val secretKey = config.getString("auth.jwt.publicKey")
    optionalHeaderValueByName("Authorization").flatMap {
      case Some(token) =>
        log.debug("token: {}", token)
        token.split(" ").lift(1) match {
          case Some(token) if isTokenExpired(token, secretKey) =>
            complete(UnauthorizedErrorException(AUTH_API_ERROR, Some("Token expired")))

          case Some(token) if Jwt.isValid(token, secretKey, Seq(JwtAlgorithm.RS256)) =>
            log.debug("jwt some : {}", token)
            provide(getClaims(token, secretKey))

          case None =>
            log.warn("Token is empty")
            complete(UnauthorizedErrorException(AUTH_API_ERROR, Some("Empty token")))

          case any =>
            log.warn("Wrong jwt token: {}", any)
            complete(UnauthorizedErrorException(AUTH_API_ERROR, Some("Wrong token")))
        }

      case None =>
        if (config.getString("env").equals("dev")) {
          provide(
            Map(
              "acl"          -> Map("ttm.wallet" -> List("full-access")),
              "phone_number" -> config.getString("auth.test.phone_number")
            )
          )
        } else {
          complete(UnauthorizedErrorException(AUTH_API_ERROR, Some("Empty token")))
        }

    }

  }

  private def getClaims(jwt: String, secretKey: String): Map[String, Any] =
    Jwt.decode(jwt, secretKey, Seq(JwtAlgorithm.RS256)) match {
      case Success(jwtClaims) =>
        log.debug("jwtClaims is {}", jwtClaims.toJson)
        parse(jwtClaims.toJson).values.asInstanceOf[Map[String, Any]]
      case Failure(exception) =>
        log.error("Exception while getting claims {}", exception)
        Map.empty
    }

  private def isTokenExpired(jwt: String, secretKey: String): Boolean =
    getClaims(jwt, secretKey).get("exp").exists(exp => exp.toString.toLong > System.currentTimeMillis())

  def checkFullVerificationAndAccess(
      authUserContext: Map[String, Any],
      customerHttpClient: CustomerHttpClient,
      onlyCustomer: Boolean = false,
      isWhiteListAuth: Boolean = false
  ): Directive1[CustomerDTO] = {

    val phoneNumber = authUserContext("phone_number").toString

    onSuccess(customerHttpClient.get(GetCustomerDTO(phoneNumber = Some(phoneNumber)))).flatMap {
      case Right(customer) =>
        val isEmailVerified  = customer.hcursor.downField("isEmailVerified").as[Boolean].getOrElse(false)
        val isSumsubVerified = customer.hcursor.downField("isSumsubVerified").as[Boolean].getOrElse(false)

        log.debug("isEmailVerified: {}, isSumsubVerified: {}", isEmailVerified, isSumsubVerified)
        decode[CustomerDTO](customer.noSpaces) match {
          case Right(customerDTO) =>
            if ((isEmailVerified && isSumsubVerified) || onlyCustomer) {

              val containsInWhitelist = whitelistIds.contains(customerDTO.customerId)

              log.debug("Whitelist is {} with {}", isWhiteListAuth, containsInWhitelist)
              if (isWhiteListAuth && containsInWhitelist)
                provide(customerDTO)
              else if (!isWhiteListAuth) provide(customerDTO)
              else complete(UnauthorizedErrorException(AUTH_API_ERROR, Some("Has no access")))
            } else complete(UnauthorizedErrorException(AUTH_API_ERROR, Some("Has no access")))
          case Left(error) =>
            complete(UnauthorizedErrorException(AUTH_API_ERROR, Some(s"Failed to decode customer: $error")))
        }

      case Left(errorInfo) =>
        complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
    }
  }
}
