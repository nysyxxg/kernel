package com.thirdparty.spark.rdd

import com.thirdparty.spark.datasource.{HBaseConnectionCache, HBaseRegion, RegionResource, TableResource}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil
import org.apache.hadoop.hbase.util.{Bytes, ShutdownHookManager}
import org.apache.spark.rdd.RDD
import org.apache.spark.{Partition, SerializableWritable, SparkContext, TaskContext}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * 实现从HBase发起多个scan的RDD
 *
 * @param sc            sparkContext
 * @param hbaseConf     HBaseConfiguration
 * @param baseScan      基础scan:每个scan共同需要设置的属性(除了startRow 和 stopRow)
 * @param startStopKeys 每个scan的startRow和stopRow 组成的数组
 */
// @author https://github.com/thirdparty-core
class HBaseScanRDD(@transient val sc: SparkContext,
                   @transient hbaseConf: Configuration,
                   @transient baseScan: Scan,
                   startStopKeys: Array[(Array[Byte], Array[Byte])]
                  ) extends RDD[Result](sc, Nil) {

  private val confBroadcast = sc.broadcast(new SerializableWritable(hbaseConf))

  def getConf: Configuration = confBroadcast.value.value

  private def toResultIterator(scaner: ResultScanner): Iterator[Result] = {
    val iterator: Iterator[Result] = new Iterator[Result] {
      var cur: Option[Result] = None

      override def hasNext: Boolean = {
        if (cur.isEmpty) {
          val r = scaner.next()
          if (r == null) {
            scaner.close()
          } else {
            cur = Some(r)
          }
        }
        cur.isDefined
      }

      override def next(): Result = {
        hasNext
        val ret = cur.get
        cur = None
        ret
      }
    }
    iterator
  }

  override def compute(split: Partition, context: TaskContext): Iterator[Result] = {
    val scanPartition = split.asInstanceOf[ScanPartition]
    val scan = TableMapReduceUtil.convertStringToScan(scanPartition.scan)
    val results = toResultIterator(TableResource(getConf).getScanner(scan))
    ShutdownHookManager.affixShutdownHook(new Thread() {
      override def run() {
        HBaseConnectionCache.close()
      }
    }, 0)
    results
  }

  /**
   * 一个scan task,经过处理后,只会scan一个region中的数据
   * 所以在发送task到 executor 中去执行时,会优先选择与要scan的
   * region的regionserver同在一个机器的executor
   */
  override def getPreferredLocations(split: Partition): Seq[String] = {
    split.asInstanceOf[ScanPartition].server.map {
      identity
    }.toSeq
  }

  override protected def getPartitions: Array[Partition] = {
    // 不做任何处理,就是一个scan 对应一个RDD的task

    /*

    val ps = new mutable.MutableList[ScanPartition]()
     for (i <- startStopKeys.indices) {
       val scan = new Scan(baseScan)
       val startStopKey = startStopKeys(i)
       scan.withStartRow(startStopKey._1)
       scan.withStopRow(startStopKey._2)
       ps += new ScanPartition(i, TableMapReduceUtil.convertScanToString(scan))
     }
     ps.toArray

     */

    // 1. 先按照startKey进行升序排序
    val sorted = startStopKeys.sortBy(_._1)(new Ordering[Array[Byte]] {
      override def compare(x: Array[Byte], y: Array[Byte]): Int = Bytes.compareTo(x, y)
    })
    // 2. 将排序后的stopkey 与 下一个的startkey进行比较,
    // 看是否相同,如果相同,说明是连续的,将两个范围进行合并,
    // 注意:合并后有可能会跨region
    val merged = merge(sorted)

    // 3. 合并后有可能会跨分区,根据分区信息将跨分区分范围进行拆分
    val regions = RegionResource(getConf).regions

    val scanRanges: Array[(Array[Byte], Array[Byte], Option[String])] = merged.flatMap(
      range =>
        acrossRegion(regions, range))

    val ps = new mutable.MutableList[ScanPartition]()
    for (i <- scanRanges.indices) {
      val scan = new Scan(baseScan)
      scan.withStartRow(scanRanges(i)._1)
      scan.withStopRow(scanRanges(i)._2)
      ps += new ScanPartition(i, TableMapReduceUtil.convertScanToString(scan), scanRanges(i)._3)
    }
    ps.toArray
  }


  /**
   * 判断range是否横跨region
   * 如果横跨region,将range拆分成两个range,保证一个scan只去一个region里面
   *
   * @param regions hbase regions
   * @param range   scan's startrow stop row
   * @return
   */
  def acrossRegion(regions: Array[HBaseRegion], range: (Array[Byte], Array[Byte])): Array[(Array[Byte], Array[Byte], Option[String])] = {
    val target = new ListBuffer[(Array[Byte], Array[Byte], Option[String])]()
    regions.foreach(region => {
      val rsStart = region.start.getOrElse(new Array[Byte](0))
      var rsEnd = region.end.getOrElse(null.asInstanceOf[Array[Byte]])
      if (rsEnd != null && Bytes.compareTo(rsEnd, new Array[Byte](0)) == 0) rsEnd = null

      if (Bytes.compareTo(rsStart, new Array[Byte](0)) == 0 && rsEnd == null) { // 只有一个region的情况,是不会横跨的
        target.append((range._1, range._2, region.server))
      } else if (Bytes.compareTo(rsStart, new Array[Byte](0)) == 0 && rsEnd != null) { // 是第一个但不是最后一个
        if (Bytes.compareTo(range._1, rsEnd) <= 0 && Bytes.compareTo(range._2, rsEnd) <= 0) {
          target.append((range._1, range._2, region.server))
        } else if (Bytes.compareTo(range._1, rsEnd) < 0 && Bytes.compareTo(range._2, rsEnd) > 0) {
          target.append((range._1, rsEnd, region.server)) // 后半段不用管,交由下一个region来处理
        }
      } else if (Bytes.compareTo(rsStart, new Array[Byte](0)) != 0 && rsEnd == null) { // 是最后一个
        if (Bytes.compareTo(range._1, rsStart) >= 0) { // 完全在这个region里面
          target.append((range._1, range._2, region.server))
        } else if (Bytes.compareTo(range._1, rsStart) < 0 && Bytes.compareTo(range._2, rsStart) > 0) {
          target.append((rsStart, range._2, region.server))
        }
      } else { // 中间的region
        if (Bytes.compareTo(range._1, rsStart) < 0 && Bytes.compareTo(range._2, rsStart) > 0 && Bytes.compareTo(range._2, rsEnd) <= 0) {
          target.append((rsStart, range._2, region.server))
        } else if (Bytes.compareTo(range._1, rsEnd) < 0 && Bytes.compareTo(range._2, rsEnd) > 0 && Bytes.compareTo(range._1, rsStart) >= 0) {
          target.append((range._1, rsEnd, region.server))
        } else if (Bytes.compareTo(range._1, rsStart) <= 0 && Bytes.compareTo(range._2, rsEnd) >= 0) { // region 完全在scan的范围内
          target.append((rsStart, rsEnd, region.server))
        } else if (Bytes.compareTo(range._1, rsStart) >= 0 && Bytes.compareTo(range._2, rsEnd) <= 0) { // scan的范围完全在region内
          target.append((range._1, range._2, region.server))
        }
      }
    })
    target.toArray
  }


  /**
   * 将scan的范围进行合并:
   * 合并时的方式为如果当前的startkey 等于上一个scan的endkey,则会将这两个范围合并成一个范围
   *
   * @return
   */
  def merge(source: Array[(Array[Byte], Array[Byte])]): Array[(Array[Byte], Array[Byte])] = {
    val target = new ListBuffer[(Array[Byte], Array[Byte])]()
    var start = source(0)._1
    var stop = source(0)._2

    for (i <- 1 until source.length) {
      if (Bytes.compareTo(stop, source(i)._1) != 0) {
        target.append((start, stop))
        start = source(i)._1
        stop = source(i)._2
      } else {
        stop = source(i)._2
      }
      if (i == source.length - 1) {
        target.append((start, stop))
      }
    }
    target.toArray
  }
}

// @author https://github.com/thirdparty-core
class ScanPartition(override val index: Int, var scan: String, val server: Option[String] = None) extends Partition
