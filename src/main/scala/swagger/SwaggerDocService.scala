package swagger

class SwaggerDocService(implicit config: Config) extends SwaggerHttpService {
  override def apiClasses: Set[Class[_]] = Set(classOf[HttpRoutes])
  override val schemes: List[String]     = List(config.getString("swagger.schemes"))
  override val host: String              = config.getString("swagger.host")
  override val info: Info                = Info(version = "1.0")
  override val unwantedDefinitions       = Seq("Function1", "Function1RequestContextFutureRouteResult")
}
