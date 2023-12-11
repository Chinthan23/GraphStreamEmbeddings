package org.graphstream
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions._
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.Trigger

object GraphStream extends App {
  val spark=SparkSession.builder().appName("Graph Stream").master("local[*]").getOrCreate()
  import spark.implicits._

  val listener = new Listener()
  spark.streams.addListener(listener)


  spark.sql("SET spark.sql.streaming.metricsEnabled=true")
  val KAFKA_TOPIC_NAME="graphs"
  val KAFKA_BOOTSTRAP_SERVERS_CONS="localhost:9092"
  println("First SparkContext:")
  println("APP Name :"+spark.sparkContext.appName)
  println("Deploy Mode :"+spark.sparkContext.deployMode)
  println("Master :"+spark.sparkContext.master)

  val edgeSchema = StructType(Array(
    StructField("src", LongType, false),
    StructField("dst", LongType, false),
    StructField("timestamp", DoubleType, false)
  ))

  val edgeStream= spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS_CONS)
    .option("subscribe", KAFKA_TOPIC_NAME)
    .option("startingOffsets", "earliest")
    .load()

  val edgeStreamData = edgeStream
    .select(from_json($"value".cast("string"), edgeSchema).as("edgeData"))
    .select(col("edgeData.src"), col("edgeData.dst"), when(col("edgeData.timestamp").isNull, lit(1.0)).otherwise(col("edgeData.timestamp")).alias("timestamp"))

  Node2vec.setup(spark.sparkContext)
  def getEmbeddings(batchDF: DataFrame, batchID: Long): Unit={
    val edgeRDD: RDD[(Long, Long, Double)] = batchDF
      .as[(Long, Long, Option[Double])]
      .rdd
      .map { case (src, dst, value) =>
        (src, dst, value.getOrElse(1.0)) // If value is None (null), use default value 1.0
      }
//    println(batchID,edgeRDD)
    Node2vec.load(edgeRDD,batchID==0)
      .initTransitionProb()
      .randomWalk()
      .embedding()
      .save(batchID)
      .cleanupAfterBatch()
  }
  val query = edgeStreamData
    .writeStream
//    .format("console")
    .foreachBatch(getEmbeddings _)
    .trigger(Trigger.ProcessingTime("30 seconds"))
    .outputMode("append")
    .start()
  spark.streams.awaitAnyTermination()
}
