package com.thirdparty.spark.datasource

import java.io.IOException

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory}

import scala.collection.mutable

// copy from https://github.com/apache/hbase-connectors
// @author https://github.com/bugboy1024
object HBaseConnectionCache {

  // A hashmap of Spark-HBase connections. Key is HBaseConnectionKey.
  val connectionMap = new mutable.HashMap[HBaseConnectionKey, SmartConnection]()

  val cacheStat = HBaseConnectionCacheStat(0, 0, 0)

  // in milliseconds
  private final val DEFAULT_TIME_OUT: Long = 10 * 60 * 1000
  private var timeout = DEFAULT_TIME_OUT
  private var closed: Boolean = false

  var housekeepingThread = new Thread(new Runnable {
    override def run() {
      while (true) {
        try {
          Thread.sleep(timeout)
        } catch {
          case e: InterruptedException =>
          // setTimeout() and close() may interrupt the sleep and it's safe
          // to ignore the exception
        }
        if (closed)
          return
        performHousekeeping(false)
      }
    }
  })
  housekeepingThread.setDaemon(true)
  housekeepingThread.start()

  def getStat: HBaseConnectionCacheStat = {
    connectionMap.synchronized {
      cacheStat.numActiveConnections = connectionMap.size
      cacheStat.copy()
    }
  }

  def close(): Unit = {
    try {
      connectionMap.synchronized {
        if (closed)
          return
        closed = true
        housekeepingThread.interrupt()
        housekeepingThread = null
        HBaseConnectionCache.performHousekeeping(true)
      }
    } catch {
      case e: Exception => println("Error in finalHouseKeeping")
    }
  }

  def performHousekeeping(forceClean: Boolean) = {
    val tsNow: Long = System.currentTimeMillis()
    connectionMap.synchronized {
      connectionMap.foreach {
        x => {
          if (x._2.refCount < 0) {
            println(s"Bug to be fixed: negative refCount of connection ${x._2}")
          }

          if (forceClean || ((x._2.refCount <= 0) && (tsNow - x._2.timestamp > timeout))) {
            try {
              x._2.connection.close()
            } catch {
              case e: IOException => println(s"Fail to close connection ${x._2}", e)
            }
            connectionMap.remove(x._1)
          }
        }
      }
    }
  }

  // For testing purpose only
  def getConnection(key: HBaseConnectionKey, conn: => Connection): SmartConnection = {
    connectionMap.synchronized {
      if (closed)
        return null
      cacheStat.numTotalRequests += 1
      val sc = connectionMap.getOrElseUpdate(key, {
        cacheStat.numActualConnectionsCreated += 1
        SmartConnection(conn)
      })
      sc.refCount += 1
      sc
    }
  }

  def getConnection(conf: Configuration): SmartConnection =
    getConnection(new HBaseConnectionKey(conf), ConnectionFactory.createConnection(conf))

  // For testing purpose only
  def setTimeout(to: Long): Unit = {
    connectionMap.synchronized {
      if (closed)
        return
      timeout = to
      housekeepingThread.interrupt()
    }
  }
}