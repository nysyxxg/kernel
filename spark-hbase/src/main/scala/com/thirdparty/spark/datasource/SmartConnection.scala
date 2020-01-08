package com.thirdparty.spark.datasource

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Admin, Connection, RegionLocator, Table}

// copy from https://github.com/apache/hbase-connectors
// @author https://github.com/bugboy1024
case class SmartConnection(connection: Connection, var refCount: Int = 0, var timestamp: Long = 0) {
  def getTable(tableName: TableName): Table = connection.getTable(tableName)

  def getRegionLocator(tableName: TableName): RegionLocator = connection.getRegionLocator(tableName)

  def isClosed: Boolean = connection.isClosed

  def getAdmin: Admin = connection.getAdmin

  def close(): Unit = {
    HBaseConnectionCache.connectionMap.synchronized {
      refCount -= 1
      if (refCount <= 0)
        timestamp = System.currentTimeMillis()
    }
  }
}