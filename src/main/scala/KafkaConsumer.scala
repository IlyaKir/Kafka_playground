import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.stream.ZStream
import zio.{ZIO, ZIOAppDefault, ZLayer}

object KafkaConsumer extends ZIOAppDefault {
  private val bootstrapServers = List("localhost:9092")
  private val consumerSettings = ConsumerSettings(bootstrapServers).withProperties(
    (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName),
    (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
  ).withGroupId("group")

  private lazy val consumerLayer = ZLayer.scoped {
    Consumer.make(settings = consumerSettings)
  }
  private lazy val consumer: ZStream[Any, Throwable, CommittableRecord[String, String]] = Consumer
    .plainStream(Subscription.topics(KafkaProducer.topicName), Serde.string, Serde.string)
    .provideLayer(consumerLayer)


  override def run: ZIO[Any, Throwable, Unit] = {
    consumer.tap { record => ZIO.succeed(println(record.record)) }
      .runDrain
  }
}
