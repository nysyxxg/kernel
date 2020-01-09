package io.github.thirdparty.core.spark.rdd

import io.github.thirdparty.core.spark.datasource.{HBaseConnectionCache, TableResource}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.{Get, Result}
import org.apache.hadoop.hbase.util.{Bytes, ShutdownHookManager}
import org.apache.spark.rdd.RDD
import org.apache.spark.{Partition, SerializableWritable, SparkContext, TaskContext}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer


/**
 * 实现从HBase批量Get的RDD
 *
 * @param sc        sparkContext
 * @param hbaseConf HBaseConfiguration
 * @param rowkeys   批量get的rowkey,每一个Array[Byte] 对应一个Get
 * @param cols      列用family:qualifier格式
 *                  如 : f1:col1
 *                  f1:col2
 *                  f2:col1
 *                  f2:col2
 * @param batch     批大小,每满足一个batch会生成一个RDD的分区
 */
// @author https://github.com/thirdparty-core
class HBaseGetRDD(@transient val sc: SparkContext,
                  @transient hbaseConf: Configuration,
                  @transient rowkeys: Array[Array[Byte]],
                  cols: Array[String] = null,
                  batch: Int = 1000
                 ) extends RDD[Result](sc, Nil) {

  val colsStr: String = if (cols != null) cols.mkString(",") else null.asInstanceOf[String]

  private val confBroadcast = sc.broadcast(new SerializableWritable(hbaseConf))

  def getConf: Configuration = confBroadcast.value.value

  override def compute(split: Partition, context: TaskContext): Iterator[Result] = {
    val partition = split.asInstanceOf[GetPartition]
    val rks = decodeRowKeys(partition.rowkeys)
    val gets = rks.map(rk => {
      val get = new Get(rk)
      if (colsStr != null) {
        colsStr.split(",").map(col => {
          val fcol = col.split(":")
          get.addColumn(Bytes.toBytes(fcol(0)), Bytes.toBytes(fcol(1)))
        })
      }
      get
    })
    val results = TableResource(getConf).get(gets.toList)
    ShutdownHookManager.affixShutdownHook(new Thread() {
      override def run() {
        HBaseConnectionCache.close()
      }
    }, 0)
    results.toIterator
  }

  private def encodeRowKeys(rowkeys: Array[Array[Byte]]): Array[Byte] = {
    val len = rowkeys.length
    var tLen = 0
    rowkeys.foreach(r => tLen += r.length)
    val res = new Array[Byte]((len + 1) * Bytes.SIZEOF_INT + tLen)
    var index = 0
    System.arraycopy(Bytes.toBytes(len), 0, res, index, Bytes.SIZEOF_INT)
    index += Bytes.SIZEOF_INT

    rowkeys.foreach(rowkey => {
      val l = rowkey.length
      System.arraycopy(Bytes.toBytes(l), 0, res, index, Bytes.SIZEOF_INT)
      index += Bytes.SIZEOF_INT
      System.arraycopy(rowkey, 0, res, index, l)
      index += rowkey.length
    })
    res
  }

  private def decodeRowKeys(rowkeys: Array[Byte]): Array[Array[Byte]] = {
    val tmp = new Array[Byte](Bytes.SIZEOF_INT)
    System.arraycopy(rowkeys, 0, tmp, 0, Bytes.SIZEOF_INT)
    val rCnt = Bytes.toInt(tmp)
    val rs = new Array[Array[Byte]](rCnt)
    var offset = 0
    offset += Bytes.SIZEOF_INT

    for (i <- 0 until rCnt) {
      val t = new Array[Byte](Bytes.SIZEOF_INT)
      System.arraycopy(rowkeys, offset, t, 0, Bytes.SIZEOF_INT)
      offset += Bytes.SIZEOF_INT
      val l = Bytes.toInt(t)
      val rowkey = new Array[Byte](l)
      System.arraycopy(rowkeys, offset, rowkey, 0, l)
      offset += l
      rs(i) = rowkey
    }
    rs
  }

  override protected def getPartitions: Array[Partition] = {
    val list = new ListBuffer[Array[Array[Byte]]]()
    val subRowkeys = new ListBuffer[Array[Byte]]
    for (elem <- rowkeys) {
      if (subRowkeys.length == batch) {
        list.append(subRowkeys.toArray)
        subRowkeys.clear()
      }
      subRowkeys.append(elem)
    }
    if (subRowkeys.nonEmpty) {
      list.append(subRowkeys.toArray)
    }

    val ps = new ListBuffer[GetPartition]()
    for (i <- list.indices) {
      ps.append(new GetPartition(i, encodeRowKeys(list(i))))
    }
    ps.toArray
  }
}

// @author https://github.com/thirdparty-core
class GetPartition(override val index: Int, val rowkeys: Array[Byte]) extends Partition