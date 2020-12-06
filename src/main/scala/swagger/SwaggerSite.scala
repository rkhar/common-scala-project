package swagger

trait SwaggerSite extends Directives {

  val swaggerSiteRoute: Route =
    path("swagger") {
      getFromResource("swagger-ui/index.html")
    } ~ getFromResourceDirectory("swagger-ui")
}
