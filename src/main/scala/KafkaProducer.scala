import org.apache.kafka.clients.producer.{ProducerConfig, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import sttp.capabilities.zio.ZioStreams
import zio._
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde._
import sttp.client4.impl.zio.ZioServerSentEvents
import sttp.client4._
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.Uri
import sttp.model.sse.ServerSentEvent
import zio.kafka.admin.{AdminClient, AdminClientSettings}
import zio.kafka.admin.AdminClient._
import zio.stream.ZStream

object KafkaProducer extends ZIOAppDefault {
  val topicName: String = "WikimediaStreamTopic"
  private val streamUri: Uri = uri"https://stream.wikimedia.org/v2/stream/recentchange"
  private val bootstrapServers = List("localhost:9092")

  private val producerSettings = ProducerSettings(bootstrapServers).withProperties(
    (ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName),
    (ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
  )
  private val producerLayer = ZLayer.scoped {
    Producer.make(settings = producerSettings)
  }

  // for kafka version < 3.0
  private lazy val safeProducerSettings = Map(
    (ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"),
    (ProducerConfig.ACKS_CONFIG, "all"),
    (ProducerConfig.RETRIES_CONFIG, Int.MaxValue.toString),
    (ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5.toString)
  )

  private lazy val highThroughputProducerSettings = Map(
    (ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"),
    (ProducerConfig.LINGER_MS_CONFIG, "20"),
    (ProducerConfig.BATCH_SIZE_CONFIG, (32*1024).toShort)
  )

  private def createSingleRecord(key: String, value: String): RIO[Producer, RecordMetadata] = {
    Producer.produce(
      topic = topicName,
      key = key,
      value = value,
      keySerializer = Serde.string,
      valueSerializer = Serde.string
    )
  }

  private def processEvents(sseStream: ZStream[Any, Throwable, ServerSentEvent]): Task[Long] = {
    sseStream.filter(_.eventType.isDefined).tap { sse =>
      createSingleRecord(key = null, value = sse.data.getOrElse(""))
    }.provideLayer(producerLayer).runCount
  }

  private def getRequest = {
    val streamResponseAs = asStream(ZioStreams) { binaryStream =>
      val sseStream = binaryStream.viaFunction(ZioServerSentEvents.parse)
      processEvents(sseStream)
    }
    basicRequest
      .get(streamUri)
      .response(streamResponseAs)
  }

  private def createNewTopic(client: AdminClient): Task[Unit] = {
    for {
      topics <- client.listTopics()
      _ <- if (topics.contains(topicName)) ZIO.unit
           else client.createTopic(NewTopic(name = topicName, numPartitions = 4, replicationFactor = 1))
             .tapError { error => ZIO.succeed(println(error.getMessage)) }
    } yield ()
  }

  private def createTopic() = {
    AdminClient.make(AdminClientSettings(bootstrapServers)).flatMap(createNewTopic)
  }

  override def run = {
    for {
      _ <- createTopic()
      backend <- HttpClientZioBackend()
      _ <- getRequest.send(backend).tapBoth(
        error => Console.printLine(s"Request error: ${error}"),
        data => data.body match {
          case Left(error) => Console.printLine(s"Response error: ${error}")
          case Right(value) => Console.printLine(s"Produced $value messages")
        }
      )
    } yield ()
  }
}