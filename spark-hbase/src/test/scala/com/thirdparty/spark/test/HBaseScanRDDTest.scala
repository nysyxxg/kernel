package com.thirdparty.spark.test

import java.util.Collections

import com.google.common.collect.Lists
import com.thirdparty.spark.rdd.HBaseScanRDD
import org.apache.hadoop.hbase.client.{ColumnFamilyDescriptorBuilder, Put, Scan, TableDescriptorBuilder}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{CellUtil, HBaseConfiguration, HBaseTestingUtility, TableName}
import org.apache.spark.sql.SparkSession

// 使用linux 环境才能运行,windows环境运行失败
// @author https://github.com/thirdparty-core
object HBaseScanRDDTest {
  val F1: Array[Byte] = Bytes.toBytes("f1")
  val F2: Array[Byte] = Bytes.toBytes("f2")
  val TABLE_NAME: TableName = TableName.valueOf("test")

  var HTU: HBaseTestingUtility = _

  def main(args: Array[String]): Unit = {
    startMiniCluster()
    createTable()
    putData()

    val spark = SparkSession.builder()
      .appName(this.getClass.getSimpleName)
      .master("local[*]")
      .getOrCreate()

    val baseScan = new Scan()
    baseScan.addFamily(F1)
    baseScan.addFamily(F2)

    // 只扫描rowkey以0001 和 0003开头的数据
    val startStopKeys = Array(
      (Bytes.toBytes("0001"), Bytes.toBytes("0002")),
      (Bytes.toBytes("0002"), Bytes.toBytes("0003"))
    )
    new HBaseScanRDD(spark.sparkContext, HTU.getConfiguration, baseScan, startStopKeys)
      .map(rs => {
        val f1c1 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F1, Bytes.toBytes("col1"))))
        val f1c2 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F1, Bytes.toBytes("col2"))))

        val f2c1 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F2, Bytes.toBytes("col1"))))
        val f2c2 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F2, Bytes.toBytes("col2"))))

        val rowkey = Bytes.toString(rs.getRow)
        (rowkey, (f1c1, f1c2, f2c1, f2c2))
      }).foreach(println)

    HTU.shutdownMiniCluster()
    HTU.shutdownMiniZKCluster()
  }

  private def putData(): Unit = {
    val table = HTU.getConnection.getTable(TABLE_NAME)
    val put1 = new Put(Bytes.toBytes("0001dhfaj"))
    put1.addColumn(F1, Bytes.toBytes("col1"), Bytes.toBytes("c1v1"))
    put1.addColumn(F1, Bytes.toBytes("col2"), Bytes.toBytes("c1v2"))
    put1.addColumn(F2, Bytes.toBytes("col1"), Bytes.toBytes("c2v1"))
    put1.addColumn(F2, Bytes.toBytes("col2"), Bytes.toBytes("c2v2"))


    val put2 = new Put(Bytes.toBytes("0002fedsarde"))
    put2.addColumn(F1, Bytes.toBytes("col1"), Bytes.toBytes("c1v1"))
    put2.addColumn(F1, Bytes.toBytes("col2"), Bytes.toBytes("c1v2"))
    put2.addColumn(F2, Bytes.toBytes("col1"), Bytes.toBytes("c2v1"))
    put2.addColumn(F2, Bytes.toBytes("col2"), Bytes.toBytes("c2v2"))


    val put3 = new Put(Bytes.toBytes("0003frewqewr"))
    put3.addColumn(F1, Bytes.toBytes("col1"), Bytes.toBytes("c1v1"))
    put3.addColumn(F1, Bytes.toBytes("col2"), Bytes.toBytes("c1v2"))
    put3.addColumn(F2, Bytes.toBytes("col1"), Bytes.toBytes("c2v1"))
    put3.addColumn(F2, Bytes.toBytes("col2"), Bytes.toBytes("c2v2"))

    val put4 = new Put(Bytes.toBytes("0004cdwqrv"))
    put4.addColumn(F1, Bytes.toBytes("col1"), Bytes.toBytes("c1v1"))
    put4.addColumn(F1, Bytes.toBytes("col2"), Bytes.toBytes("c1v2"))
    put4.addColumn(F2, Bytes.toBytes("col1"), Bytes.toBytes("c2v1"))
    put4.addColumn(F2, Bytes.toBytes("col2"), Bytes.toBytes("c2v2"))
    table.put(Lists.newArrayList(put1, put2, put3, put4))
    table.close()
  }

  private def createTable(): Unit = {
    val f1 = ColumnFamilyDescriptorBuilder.newBuilder(F1).build()
    val f2 = ColumnFamilyDescriptorBuilder.newBuilder(F2).build()
    val families = List(f1, f2)
    import scala.collection.JavaConverters._
    val list = families.asJavaCollection
    Collections.addAll(list, f1, f2)
    val desc = TableDescriptorBuilder.newBuilder(TABLE_NAME)
      .setColumnFamilies(list)
      .build()

    HTU.getAdmin.createTable(desc)
  }

  def startMiniCluster(): Unit = {
    val hbaseConf = HBaseConfiguration.create()
    HTU = new HBaseTestingUtility(hbaseConf)
    HTU.startMiniZKCluster()
    HTU.startMiniCluster(3)
  }
}
