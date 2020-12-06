package repository

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait ElasticSearchRepository[T <: AnyRef] extends ElasticDsl {
  implicit val ec: ExecutionContext
  implicit val manifest: Manifest[T]
  implicit val scheduler: Scheduler

  implicit object EntityIndexable extends Indexable[T] {
    override def json(entity: T): String = encode(entity)
  }

  val log: Logger = LoggerFactory.getLogger(getClass)

  def encode: T => String

  def decode: String => T

  def elasticSearchClient: ElasticClient

  def indexName: String

  def shards: Int

  def replicas: Int

  def createIndexIfNotExists(): Unit = {
    var index = createIndex(indexName)
      .shards(shards)
      .replicas(replicas)
      .indexSetting("mapping", Map("total_fields" -> Map("limit" -> totalMappingFieldLimit)))
    if (mapping.isDefined) {
      index = index.mappings(mapping.get)
    }

    val result: Try[Response[CreateIndexResponse]] = Try(elasticSearchClient.execute(index).await)

    result match {
      case Success(value) =>
        value match {
          case _: RequestSuccess[_] =>
            log.info("Index {} was successfully created", indexName)
          case r: RequestFailure =>
            log.warn(
              "Index {} wasn't created, cause {}",
              indexName,
              r.error.reason
            )
        }
      case Failure(_) =>
        log.error("Index {} already exists", indexName)
    }
  }

  def totalMappingFieldLimit: Int = 1000

  def mapping: Option[MappingDefinition] = None

  def dropIndexIfExists(): Unit = {
    val result: Try[Response[DeleteIndexResponse]] =
      Try(
        elasticSearchClient.execute(deleteIndex(indexName)).await
      )

    result match {
      case Success(value) =>
        value match {
          case _: RequestSuccess[_] =>
            log.info("Index {} was successfully deleted", indexName)
          case r: RequestFailure =>
            log.warn(
              "Index {} wasn't deleted, cause {}",
              indexName,
              r.error.reason
            )
        }
      case Failure(_) =>
        log.error("Index {} didn't exist", indexName)
    }
  }

  def edit(id: String, query: String): Future[Unit] = {
    val response: Future[Response[UpdateResponse]] = elasticSearchClient.execute {
      updateById(indexName, indexName, id).script("ctx._source." + query)
    }

    response.map { r =>
      if (r.isError) {
        throw new Exception(r.error.reason)
      }
    }
  }

  def merge(id: String, entity: T): Future[T] = {
    val response = elasticSearchClient.execute {
      updateById(indexName, indexName, id)
        .refresh(RefreshPolicy.Immediate)
        .docAsUpsert(entity)
    }

    response.map { r =>
      if (r.isSuccess) {
        entity
      } else {
        throw new Exception(r.error.reason)
      }
    }
  }

  def merge(id: String, json: String): Future[Response[UpdateResponse]] = {
    val response: Future[Response[UpdateResponse]] = elasticSearchClient.execute {
      updateById(indexName, indexName, id)
        .refresh(RefreshPolicy.Immediate)
        .doc(json)
    }

    response
  }

  def insert(entity: T, id: String): Future[T] =
    elasticSearchClient.execute {
      indexInto(indexName, indexName).doc(entity).id(id)
    }.map { response =>
      if (response.isSuccess) entity
      else
        throw new Exception(response.error.reason)
    }

  //  def insert(entity: T, id: String): Future[T] =
//    upsert(entity, Some(id), createOnly = true)

  def upsert(entity: T, idOpt: Option[String] = None, createOnly: Boolean = false): Future[T] = {

    val response = elasticSearchClient.execute {
      var i = indexInto(indexName)
        .doc(entity)
        .createOnly(createOnly)
        .refreshImmediately

      idOpt.foreach { id =>
        i = i.withId(id)
      }

      i
    }

    response.map { r =>
      if (r.isSuccess) {
        entity
      } else {
        throw new Exception(r.error.reason)
      }
    }
  }

  def update(entity: String, idOpt: Option[String] = None): Future[Json] = {

    val response = elasticSearchClient.execute {
      var i = indexInto(indexName)
        .doc(entity)
        .refreshImmediately

      idOpt.foreach { id =>
        i = i.withId(id)
      }

      i
    }

    response.map { r =>
      if (r.isSuccess) {
        entity.asJson
      } else {
        throw new Exception(r.error.reason)
      }
    }
  }

  def find(id: String, attempts: Int = 5): Future[Option[T]] = {
    val result = () =>
      elasticSearchClient.execute {
        search(indexName)
          .query(idsQuery(id))
      }

    val retriedResult = akka.pattern.retry(result, attempts, 100.milliseconds)

    retriedResult.map { r =>
      if (r.isSuccess && r.result.hits.total > 0) {
        Some(decode(r.result.hits.hits.head.sourceAsString))
      } else {
        None
      }
    }
  }

  def findAll(ids: List[String], attempts: Int = 5): Future[Response[SearchResponse]] = {
    val result = () =>
      elasticSearchClient.execute {
        search(indexName)
          .query(idsQuery(ids))
      }

    akka.pattern.retry(result, attempts, 100.milliseconds)

  }

  /**
    * Search query
    *
    * @param searchRequest SearchRequest
    * @return
    */
  def search(searchRequest: SearchRequest, attempts: Int = 5): Future[Seq[T]] = {
    val result = () =>
      elasticSearchClient.execute {
        searchRequest
      }

    val retriedResult = akka.pattern.retry(result, attempts, 100.milliseconds)

    retriedResult.map { r =>
      val hits = r.result.hits.hits.map(_.sourceAsString)
      var res  = scala.collection.mutable.ListBuffer.empty[T]
      hits.foreach { h =>
        val t: T = decode(h)
        res += t
      }

      res.toList
    }
  }

  /**
    * Search raw query
    *
    * @param searchRequest SearchRequest
    * @return
    */
  def searchRaw(searchRequest: SearchRequest, attempts: Int = 5): Future[Response[SearchResponse]] = {
    val result = () =>
      elasticSearchClient.execute {
        searchRequest
      }

    akka.pattern.retry(result, attempts, 100.milliseconds)
  }
}
