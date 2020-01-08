package com.thirdparty.spark.examples

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration

// @author https://github.com/bugboy1024
class HBaseConf {
  val conf: Configuration = HBaseConfiguration.create()
  conf.set("fs.defaultFS", "hdfs://xxxx:8020")
  conf.set("hbase.zookeeper.quorum", "xxxx")
  conf.set("hbase.zookeeper.property.clientPort", "2181")
  conf.set("zookeeper.znode.parent", "/xxxx")
  conf.set("hbase.client.retries.number", "1")

  def getConf: Configuration = conf
}
