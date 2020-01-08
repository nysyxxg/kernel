package com.thirdparty.spark.datasource

// copy from https://github.com/apache/hbase-connectors
// @author https://github.com/thirdparty-core
trait ReferencedResource {
  var count: Int = 0

  def init(): Unit

  def destroy(): Unit

  def acquire(): Unit = synchronized {
    try {
      count += 1
      if (count == 1) {
        init()
      }
    } catch {
      case e: Throwable =>
        release()
        throw e
    }
  }

  def release(): Unit = synchronized {
    count -= 1
    if (count == 0) {
      destroy()
    }
  }

  def releaseOnException[T](func: => T): T = {
    acquire()
    val ret = {
      try {
        func
      } catch {
        case e: Throwable =>
          release()
          throw e
      }
    }
    ret
  }
}