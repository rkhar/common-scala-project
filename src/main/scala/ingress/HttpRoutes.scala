package ingress

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

@Path("/api/v1/fintel")
@SecurityScheme(
  name = "JWT_AUTH",
  description = "Add header: `Authorization: <jwt>`. Now support Griffon",
  `type` = SecuritySchemeType.HTTP,
  in = SecuritySchemeIn.HEADER,
  scheme = "bearer",
  paramName = "Authorization",
  bearerFormat = "JWT"
)
@security.SecurityRequirement(name = "JWT_AUTH")
class HttpRoutes(
    override val customerHttpClient: CustomerHttpClient,
    override val customerService: CustomerService,
    override val walletHttpClient: WalletHttpClient,
    override val documentHttpClient: DocumentHttpClient,
    override val zeebeHttpClient: ZeebeHttpClient,
    override val statisticsClient: StatisticsClient,
    override val notificationHttpClient: NotificationHttpClient,
    override val faqHttpClient: FaqHttpClient,
    override val financeHttpClient: FinanceHttpClient
)(implicit val system: ActorSystem[_], val config: Config)
    extends CustomerHttpRoutes
    with WalletHttpRoutes
    with DocumentHttpRoutes
    with StatisticsHttpRoutes
    with NotificationHttpRoutes
    with FaqHttpRoutes
    with RequestTimeoutHandler
    with PredefinedFromStringUnmarshallers
    with DepositHttpRoutes {

  protected val log: Logger = LoggerFactory.getLogger(getClass)

  implicit val classicSystem: actor.ActorSystem = system.toClassic

  lazy val requestTimeout: Duration = FiniteDuration(
    system.settings.config.getConfig("http-server").getDuration("ask-timeout").toNanos,
    TimeUnit.NANOSECONDS
  )

  val routes: Route = withRequestTimeout(requestTimeout, timeoutHandler) {

    pathPrefix("v1" / "fintel") {
      concat(
        healthCheck,
        statisticsHttpRoutes,
        customerHttpRoutes,
        documentHttpRoutes,
        notificationHttpRoutes,
        depositHttpRoutes,
        walletHttpRoutes,
        faqHttpRoutes
      )
    }

  }

  @GET
  @Operation(
    summary = "API health check",
    description = "Just returns random text with status code 200 if everything is OK",
    method = "GET",
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "Just a random text",
        content = Array(
          new Content(
            examples = Array(new ExampleObject(name = "Singe response", value = "Fintel RESTful API is greeting you!")),
            mediaType = "application/json"
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/healthcheck")
  @Tag(name = "Health check")
  def healthCheck: Route = path("healthcheck") {
    get {
      complete("Fintel RESTful API is greeting you!")
    }
  }

}
