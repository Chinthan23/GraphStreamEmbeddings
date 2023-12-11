package org.graphstream

import org.apache.spark.SparkContext
import org.apache.spark.mllib.feature.{Word2Vec, Word2VecModel}
import org.apache.spark.rdd.RDD

object Word2vec extends Serializable {
  var context: SparkContext = null
  var word2vec = new Word2Vec()
  var model: Word2VecModel = null

  def setup(context: SparkContext, learningRate: Double, iterations: Int, numPartition: Int, dim: Int): this.type = {
    this.context = context
    /**
     * model = sg
     * update = hs
     */
    word2vec.setLearningRate(learningRate)
      .setNumIterations(iterations)
      .setNumPartitions(numPartition)
      .setMinCount(0)
      .setVectorSize(dim)
      .setWindowSize(10)

//    val word2vecWindowField = word2vec.getClass.getDeclaredField("org$apache$spark$mllib$feature$Word2Vec$$window")
//    word2vecWindowField.setAccessible(true)
//    word2vecWindowField.setInt(word2vec, param.window)

    this
  }

  def read(path: String): RDD[Iterable[String]] = {
    context.textFile(path).repartition(5).map(_.split("\\s").toSeq)
  }

  def fit(input: RDD[Iterable[String]]): this.type = {
    if(!input.isEmpty()){
      model = word2vec.fit(input)
    }
    this
  }

  def save(outputPath: String): this.type = {
    println(outputPath)
    if(model!=null)
      model.save(context, s"$outputPath.bin")
    this
  }

  def load(path: String): this.type = {
    model = Word2VecModel.load(context, path)

    this
  }

  def getVectors = {
    if(model!=null)
      Option(this.model.getVectors)
    else{
      null
    }
  }


}
