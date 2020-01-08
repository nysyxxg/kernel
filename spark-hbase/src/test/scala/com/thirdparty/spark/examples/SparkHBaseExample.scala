package com.thirdparty.spark.examples

import com.thirdparty.spark.rdd.{HBaseGetRDD, HBaseScanRDD}
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{CellUtil, TableName}
import org.apache.spark.sql.SparkSession

// @author https://github.com/thirdparty-core
object SparkHBaseExample extends HBaseConf {

  val F1: Array[Byte] = Bytes.toBytes("f1")
  val F2: Array[Byte] = Bytes.toBytes("f2")
  val TABLE_NAME: TableName = TableName.valueOf("test")

  val COL1: Array[Byte] = Bytes.toBytes("col1")
  val COL2: Array[Byte] = Bytes.toBytes("col2")


  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .master("local[*]")
      .appName(this.getClass.getSimpleName)
      .getOrCreate()

    // scan
    val scan = new Scan()
    scan.addFamily(Bytes.toBytes("f1"))
    scan.addFamily(Bytes.toBytes("f2"))

    //扫描rowkey范围：
    // 000100-000200
    // 000500-000600
    // 000900-001100
    // 001200-001300
    // 001600-001700
    // 001800-001900

    val startStopKeys = Array(
      (Bytes.toBytes("000100"), Bytes.toBytes("000200")),
      (Bytes.toBytes("000500"), Bytes.toBytes("000700")),
      (Bytes.toBytes("001200"), Bytes.toBytes("001300")),
      (Bytes.toBytes("000800"), Bytes.toBytes("001100")),
      (Bytes.toBytes("001800"), Bytes.toBytes("001900")),
      (Bytes.toBytes("001600"), Bytes.toBytes("001700"))
    )
    val conf = getConf
    conf.set(TableInputFormat.INPUT_TABLE, "test")

    import spark.implicits._
    new HBaseScanRDD(spark.sparkContext, conf, scan, startStopKeys)
      .map(rs => {
        val rowkey = Bytes.toString(rs.getRow)
        val f1c1 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F1, COL1)))
        val f1c2 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F1, COL2)))
        val f2c1 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F2, COL1)))
        val f2c2 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F2, COL2)))
        ("scan", rowkey, f1c1, f1c2, f2c1, f2c2)
      }).toDF().show(100000)

    val rowkeys = Array(
      "000001d222bfb3-8",
      "000002d222bfb3-8",
      "000003d222bfb3-8",
      "000004d222bfb3-8",
      "000005d222bfb3-8",
      "000006d222bfb3-8",
      "000007d222bfb3-8",
      "000008d222bfb3-8",
      "000009d222bfb3-8",
      "000010d222bfb3-8",
      "000011d222bfb3-8",
      "000012d222bfb3-8",
      "000013d222bfb3-8",
      "000014d222bfb3-8",
      "000015d222bfb3-8",
      "000016d222bfb3-8",
      "000017d222bfb3-8",
      "000018d222bfb3-8",
      "000019d222bfb3-8",
      "000020d222bfb3-8",
      "000021d222bfb3-8",
      "000022d222bfb3-8",
      "000023d222bfb3-8",
      "000024d222bfb3-8",
      "000025d222bfb3-8",
      "000026d222bfb3-8",
      "000027d222bfb3-8",
      "000028d222bfb3-8",
      "000029d222bfb3-8",
      "000030d222bfb3-8",
      "000031d222bfb3-8",
      "000032d222bfb3-8",
      "000033d222bfb3-8",
      "000034d222bfb3-8",
      "000035d222bfb3-8",
      "000036d222bfb3-8",
      "000037d222bfb3-8",
      "000038d222bfb3-8",
      "000039d222bfb3-8",
      "000040d222bfb3-8",
      "000041d222bfb3-8",
      "000042d222bfb3-8",
      "000043d222bfb3-8",
      "000044d222bfb3-8",
      "000045d222bfb3-8",
      "000046d222bfb3-8",
      "000047d222bfb3-8",
      "000048d222bfb3-8",
      "000049d222bfb3-8",
      "000050d222bfb3-8",
      "000051d222bfb3-8",
      "000052d222bfb3-8").map(Bytes.toBytes)
    val cols = Array("f1:col1", "f1:col2", "f2:col1", "f2:col2")
    new HBaseGetRDD(spark.sparkContext, conf, rowkeys, cols, 10)
      .map(rs => {
        val rowkey = Bytes.toString(rs.getRow)
        val f1c1 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F1, COL1)))
        val f1c2 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F1, COL2)))
        val f2c1 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F2, COL1)))
        val f2c2 = Bytes.toString(CellUtil.cloneValue(rs.getColumnLatestCell(F2, COL2)))
        ("get", rowkey, f1c1, f1c2, f2c1, f2c2)
      }).toDF().show(100000)

    spark.stop()
  }


}
