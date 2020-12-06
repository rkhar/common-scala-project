package ingress

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class HttpServer(routes: Route, interface: String, port: Int, system: ActorSystem[_], config: Config)
    extends CustomRejectionHandlers
    with SwaggerSite
    with RouteConcatenation {
  implicit val classicSystem: classic.ActorSystem = system.toClassic
  private val shutdown                            = CoordinatedShutdown(classicSystem)

  def start(): Unit = {

    val aggregatedRoutes = config.getString("env") match {
      case "dev" =>
        cors()(
          concat(
            routes,
            swaggerSiteRoute,
            new SwaggerDocService()(config).routes
          )
        )
      case "qa" =>
        cors()(
          concat(
            routes,
            swaggerSiteRoute,
            new SwaggerDocService()(config).routes
          )
        )
      case "prod" =>
        cors()(
          concat(
            routes
          )
        )
    }

    Http().bindAndHandle(aggregatedRoutes, interface, port).onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("main-api online at http://{}:{}/", address.getHostString, address.getPort)

        shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "http-graceful-terminate") { () =>
          binding.terminate(10.seconds).map { _ =>
            system.log
              .info("main-api http://{}:{}/ graceful shutdown completed", address.getHostString, address.getPort)
            Done
          }
        }
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

}
