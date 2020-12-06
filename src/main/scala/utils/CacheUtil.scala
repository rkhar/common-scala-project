package utils

class CacheUtil {
  private var cache: Map[String, (String, DateTime)] = Map.empty
  val log: Logger                                    = LoggerFactory.getLogger(getClass)

  def add(key: String, value: String): Unit =
    try {
      if (check(key) == 0)
        this.cache.updated(key, (value, DateTime.now()))
      else if (check(key) == -1)
        this.cache = this.cache + (key -> ((value, DateTime.now())))
    } catch {
      case exception: Exception =>
        log.error("exception while cache token: " + exception.getMessage)
    }

  private def check(key: String) = {
    if (!this.cache.contains(key))
      -1
    else if (this.cache.contains(key) && DateTime.now().getMillis - this.cache(key)._2.getMillis > 300000)
      0
    1
  }
}
