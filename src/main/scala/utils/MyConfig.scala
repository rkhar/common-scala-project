package utils

object MyConfig {

  implicit class RichConfig(val config: Config) extends AnyVal {

    def optionalString(path: String): Option[String] =
      if (config.hasPath(path)) {
        Some(config.getString(path))
      } else {
        None
      }

  }
}
