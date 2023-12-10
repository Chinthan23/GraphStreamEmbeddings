package org.graphstream

import org.apache.spark.sql.streaming.StreamingQueryListener
import org.apache.spark.sql.streaming.StreamingQueryListener._


class Listener extends StreamingQueryListener {

  override def onQueryStarted(event: QueryStartedEvent): Unit = {
    println("start:" + event.timestamp)
  }

  override def onQueryProgress(event: QueryProgressEvent): Unit = {
    val latency = event.progress.durationMs.get("triggerExecution")/3000.0;
    val throughput = event.progress.processedRowsPerSecond
    val inputRows = event.progress.numInputRows
    println("inputRows: " + inputRows);
    println("latency: " + latency);
    println("throughput: " + throughput);
    println()
  }

  override def onQueryTerminated(event: QueryTerminatedEvent): Unit = {
    println("term:" + event.id)
  }
}
