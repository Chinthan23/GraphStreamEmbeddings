package org.graphstream


import java.io.Serializable
import scala.util.Try
import scala.collection.mutable.ArrayBuffer
import org.slf4j.{Logger, LoggerFactory}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.{EdgeTriplet, Graph, _}
import graph.{EdgeAttr, GraphOps, NodeAttr}

import scala.util.control.Breaks.{break, breakable}

object Node2vec extends Serializable {
  lazy val logger: Logger = LoggerFactory.getLogger(getClass.getName);

  var context: SparkContext = null
  var degree: Int = 10000
  var numWalks: Int = 10
  var directed: Boolean = true
  var p: Double = 1.0
  var q: Double = 1.0
  var walkLength: Int = 20
//  var node2id: RDD[(String, Long)] = null
  var indexedEdges: RDD[Edge[EdgeAttr]] = _
  var indexedNodes: RDD[(VertexId, NodeAttr)] = _
  var graph: Graph[NodeAttr, EdgeAttr] = _
  var initialGraph: Graph[NodeAttr, EdgeAttr] = _
  var randomWalkPaths: RDD[(Long, ArrayBuffer[Long])] = null
  var learningRate: Double= 0.02
  var iterations: Int = 5
  var numPartition: Int = 2
  var dim: Int = 128
  var output: String = "target/output/embeddings_after/window"
  def setup(context: SparkContext, learningRate: Double=0.02, iterations: Int=5, numPartition: Int=2, dim: Int=128 ): this.type = {
    this.context = context
    this.learningRate=learningRate
    this.iterations=iterations
    this.numPartition=numPartition
    this.dim=dim

    this
  }

  def load(graphRDD: RDD[(Long,Long,Double)], firstBatch: Boolean=false): this.type = {
    val bcMaxDegree = context.broadcast(degree)
    val bcEdgeCreator = directed match {
      case true => context.broadcast(GraphOps.createDirectedEdge)
      case false => context.broadcast(GraphOps.createUndirectedEdge)
    }

    val inputTriplets: RDD[(Long, Long, Double)] = graphRDD

    val newIndexedNodes = inputTriplets.flatMap { case (srcId, dstId, weight) =>
      bcEdgeCreator.value.apply(srcId, dstId, weight)
    }.reduceByKey(_++_).map { case (nodeId, neighbors: Array[(VertexId, Double)]) =>
      var neighbors_ = neighbors
      if (neighbors_.length > bcMaxDegree.value) {
        neighbors_ = neighbors.sortWith{ case (left, right) => left._2 > right._2 }.slice(0, bcMaxDegree.value)
      }
      (nodeId, NodeAttr(neighbors = neighbors_.distinct))
    }.repartition(10).cache()

    val newIndexedEdges = newIndexedNodes.flatMap { case (srcId, clickNode) =>
      clickNode.neighbors.map { case (dstId, weight) =>
        Edge(srcId, dstId, EdgeAttr())
      }
    }.repartition(10).cache()
    if(firstBatch){
      indexedNodes=newIndexedNodes
      indexedEdges=newIndexedEdges
    }
    else {
      indexedNodes = indexedNodes.union(newIndexedNodes).reduceByKey
      { (nodeAttr1, nodeAttr2) =>
        nodeAttr1.copy(neighbors = (nodeAttr1.neighbors ++ nodeAttr2.neighbors).distinct)
      }.repartition(10).cache()
      indexedEdges = indexedEdges.union(newIndexedEdges).repartition(10).cache()
    }

    this
  }
  def initTransitionProb(): this.type = {
    val bcP = context.broadcast(p)
    val bcQ = context.broadcast(q)

    graph = Graph(indexedNodes, indexedEdges)
      .mapVertices[NodeAttr] { case (vertexId, clickNode) =>
        if(clickNode!=null) {
          val (j, q) = GraphOps.setupAlias(clickNode.neighbors)
          val nextNodeIndex = GraphOps.drawAlias(j, q)
          clickNode.path = Array(vertexId, clickNode.neighbors(nextNodeIndex)._1)
        }
        clickNode
      }
      .mapTriplets { edgeTriplet: EdgeTriplet[NodeAttr, EdgeAttr] =>
        if(edgeTriplet.srcAttr!=null && edgeTriplet.dstAttr!=null){
          val (j, q) = GraphOps.setupEdgeAlias(bcP.value, bcQ.value)(edgeTriplet.srcId, edgeTriplet.srcAttr.neighbors, edgeTriplet.dstAttr.neighbors)
          edgeTriplet.attr.J = j
          edgeTriplet.attr.q = q
          edgeTriplet.attr.dstNeighbors = edgeTriplet.dstAttr.neighbors.map(_._1)

          edgeTriplet.attr
        }
        else{
          edgeTriplet.attr
        }
      }.cache

    this
  }

  def randomWalk(): this.type = {
    val edge2attr = graph.triplets.map { edgeTriplet =>
      (s"${edgeTriplet.srcId}${edgeTriplet.dstId}", edgeTriplet.attr)
    }.repartition(10).cache
    edge2attr.first

    for (iter <- 0 until numWalks) {
      var prevWalk: RDD[(Long, ArrayBuffer[Long])] = null
      var randomWalk = graph.vertices.filter{case (_,clickNode) => clickNode!=null}
        .map { case (nodeId, clickNode) =>
          val pathBuffer = new ArrayBuffer[Long]()
          pathBuffer.append(clickNode.path:_*)
          (nodeId, pathBuffer)
      }.cache
      var activeWalks = randomWalk.first
      graph.unpersist(blocking = false)
      graph.edges.unpersist(blocking = false)
      breakable{
        for (walkCount <- 0 until walkLength) {
          prevWalk = randomWalk
          randomWalk = randomWalk.filter{ case (_, pathBuffer)=> pathBuffer !=null }
          .map { case (srcNodeId, pathBuffer) =>
          val prevNodeId = pathBuffer(pathBuffer.length - 2)
          val currentNodeId = pathBuffer.last

          (s"$prevNodeId$currentNodeId", (srcNodeId, pathBuffer))
        }.join(edge2attr).filter{case (_,((_,_),attr))=> attr.dstNeighbors.length>0 }
          .map { case (edge, ((srcNodeId, pathBuffer), attr)) =>
          try {
            val nextNodeIndex = GraphOps.drawAlias(attr.J, attr.q)
            val nextNodeId = attr.dstNeighbors(nextNodeIndex)
            pathBuffer.append(nextNodeId)

            (srcNodeId, pathBuffer)
          } catch {
          case e: Exception => throw new RuntimeException(e.getMessage)
          }
        }.cache
        if(!randomWalk.isEmpty()) {
          activeWalks = randomWalk.first
        }
        else{
          break
        }
          prevWalk.unpersist(blocking=false)
        }
      }


      if (randomWalkPaths != null) {
        val prevRandomWalkPaths = randomWalkPaths
        randomWalkPaths = randomWalkPaths.union(randomWalk).cache()
//        randomWalkPaths.first
        prevRandomWalkPaths.unpersist(blocking = false)
      } else {
        randomWalkPaths = randomWalk
      }
    }

    this
  }

  def embedding(): this.type = {
    val randomPaths = randomWalkPaths.map { case (vertexId, pathBuffer) =>
      Try(pathBuffer.map(_.toString).toIterable).getOrElse(null)
    }.filter(_!=null)

    Word2vec.setup(this.context, this.learningRate, this.iterations, this.numPartition, this.dim).fit(randomPaths)

    this
  }

  def save(batchid: Long): this.type = {
    this.saveRandomPath(batchid)
      .saveModel(batchid)
      .saveVectors(batchid)
  }

  def saveRandomPath(batchid: Long): this.type = {
    randomWalkPaths
      .map { case (vertexId, pathBuffer) =>
        Try(pathBuffer.mkString("\t")).getOrElse(null)
      }
      .filter(x => x != null && x.replaceAll("\\s", "").length > 0)
      .repartition(10)
      .saveAsTextFile(s"${output}_${batchid}_randomPath")

    this
  }

  def saveModel(batchid: Long): this.type = {
    Word2vec.save(s"${output}_${batchid}_model")

    this
  }

  def saveVectors(batchid: Long): this.type = {
    val vectorsOption = Word2vec.getVectors
    if(vectorsOption!=null){
      vectorsOption.foreach { vectors =>
        val node2vector = context.parallelize(vectors.toList)
        .map { case (nodeId, vector) =>
        (nodeId.toLong, vector.mkString(","))
      }

        node2vector.map { case (nodeId, vector) => s"$nodeId\t$vector" }
        .repartition(10)
        .saveAsTextFile(s"${output}_${batchid}.emb")
      }
    }

    this
  }

  def cleanupAfterBatch(): this.type={
    graph.unpersist(blocking = false)
    randomWalkPaths.unpersist(blocking = false)
    graph=initialGraph
    randomWalkPaths=null
    this
  }
  def cleanup(): this.type = {
//    node2id.unpersist(blocking = false)
    indexedEdges.unpersist(blocking = false)
    indexedNodes.unpersist(blocking = false)
    graph.unpersist(blocking = false)
    randomWalkPaths.unpersist(blocking = false)

    this
  }

}