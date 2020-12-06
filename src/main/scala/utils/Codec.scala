package utils

import scala.util.Try

trait Codec {

  val dateTimeFormatter: DateTimeFormatter            = DateTimeFormat.forPattern("yyyy-MM-dd")
  val dateTimeFormatterWithTime: DateTimeFormatter    = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
  val dateTimeFormatterWithSeconds: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss+SSSS")
  val defaultDateTimeFormatter: DateTimeFormatter     = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

  def dateTimeFormatDecoder(format: DateTimeFormatter): Decoder[DateTime] =
    Decoder[String].emapTry(str => Try(DateTime.parse(str, format)))

  implicit val jodaDateTimeEncoder: Encoder[DateTime] =
    Encoder[String].contramap(_.toString("yyyy-MM-dd'T'HH:mm:ss'Z'"))

  implicit val jodaDateTimeDecoder: Decoder[DateTime] =
    dateTimeFormatDecoder(dateTimeFormatter)
      .or(dateTimeFormatDecoder(dateTimeFormatterWithTime))
      .or(dateTimeFormatDecoder(dateTimeFormatterWithSeconds))
      .or(dateTimeFormatDecoder(defaultDateTimeFormatter))

  implicit val encodeDomainEntity: Encoder[DomainEntity] = Encoder.instance {
    case e: Accepted => e.asJson.dropNullValues
    case e: Customer => e.asJson.dropNullValues
    case e: Topic    => e.asJson.dropNullValues
    case e: Question => e.asJson.dropNullValues
  }

  implicit val decodeDomainEntity: Decoder[DomainEntity] =
    List[Decoder[DomainEntity]](Decoder[DomainEntity].widen).reduceLeft(_.or(_))
}
