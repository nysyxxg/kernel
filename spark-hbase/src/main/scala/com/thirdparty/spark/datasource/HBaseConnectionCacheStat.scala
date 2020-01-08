package com.thirdparty.spark.datasource

// copy from https://github.com/apache/hbase-connectors
// @author https://github.com/bugboy1024
case class HBaseConnectionCacheStat(var numTotalRequests: Long,
                                    var numActualConnectionsCreated: Long,
                                    var numActiveConnections: Long)
