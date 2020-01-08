package com.thirdparty.spark.datasource

// copy from https://github.com/apache/hbase-connectors
// @author https://github.com/thirdparty-core
case class HBaseConnectionCacheStat(var numTotalRequests: Long,
                                    var numActualConnectionsCreated: Long,
                                    var numActiveConnections: Long)
