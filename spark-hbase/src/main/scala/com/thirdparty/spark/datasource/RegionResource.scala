package com.thirdparty.spark.datasource

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.RegionLocator
import org.apache.hadoop.hbase.mapreduce.TableInputFormat

// copy from https://github.com/apache/hbase-connectors
// @author https://github.com/thirdparty-core
case class RegionResource(hbaseConf: Configuration) extends ReferencedResource {
  var connection: SmartConnection = _
  var rl: RegionLocator = _
  val regions: Array[HBaseRegion] = releaseOnException {
    val keys = rl.getStartEndKeys
    keys.getFirst.zip(keys.getSecond)
      .zipWithIndex
      .map(x =>
        HBaseRegion(Some(x._1._1),
          Some(x._1._2),
          Some(rl.getRegionLocation(x._1._1).getHostname)))
  }

  override def init(): Unit = {
    connection = HBaseConnectionCache.getConnection(hbaseConf)
    rl = connection.getRegionLocator(TableName.valueOf(hbaseConf.get(TableInputFormat.INPUT_TABLE)))
  }

  override def destroy(): Unit = {
    if (rl != null) {
      rl.close()
      rl = null
    }
    if (connection != null) {
      connection.close()
      connection = null
    }
  }
}
