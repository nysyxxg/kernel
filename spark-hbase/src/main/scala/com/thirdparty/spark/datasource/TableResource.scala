package com.thirdparty.spark.datasource

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Result, ResultScanner, Scan, Table}
import org.apache.hadoop.hbase.mapreduce.TableInputFormat

// copy from https://github.com/apache/hbase-connectors
// @author https://github.com/bugboy1024
case class TableResource(hbaseConf: Configuration) extends ReferencedResource {
  var connection: SmartConnection = _
  var table: Table = _

  override def init(): Unit = {
    connection = HBaseConnectionCache.getConnection(hbaseConf)
    table = connection.getTable(TableName.valueOf(hbaseConf.get(TableInputFormat.INPUT_TABLE)))
  }

  override def destroy(): Unit = {
    if (table != null) {
      table.close()
      table = null
    }
    if (connection != null) {
      connection.close()
      connection = null
    }
  }

  def getScanner(scan: Scan): ResultScanner = releaseOnException {
    table.getScanner(scan)
  }

  def get(list: java.util.List[org.apache.hadoop.hbase.client.Get]): Array[Result] = releaseOnException {
    table.get(list)
  }
}

