package com.thirdparty.spark.examples

import java.util.{Collections, UUID}

import com.google.common.collect.Lists
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.util.Bytes

// @author https://github.com/bugboy1024
object MockData extends HBaseConf {
  val F1: Array[Byte] = Bytes.toBytes("f1")
  val F2: Array[Byte] = Bytes.toBytes("f2")
  val TABLE_NAME: TableName = TableName.valueOf("test")

  val COL1: Array[Byte] = Bytes.toBytes("col1")
  val COL2: Array[Byte] = Bytes.toBytes("col2")

  def main(args: Array[String]): Unit = {
    val conn = ConnectionFactory.createConnection(getConf)
    val admin = conn.getAdmin
    if (!admin.tableExists(TABLE_NAME)) {
      val f1 = ColumnFamilyDescriptorBuilder.newBuilder(F1).build()
      val f2 = ColumnFamilyDescriptorBuilder.newBuilder(F2).build()
      val families = Lists.newArrayList[ColumnFamilyDescriptor]()
      Collections.addAll(families, f1, f2)
      val desc = TableDescriptorBuilder.newBuilder(TABLE_NAME)
        .setColumnFamilies(families)
        .build()
      admin.createTable(desc)
    }

    val table = conn.getTable(TABLE_NAME)

    val puts = Lists.newArrayList[Put]()
    val uuid = UUID.randomUUID()
    for (i <- 1 until 100000) {
      val put = new Put(Bytes.toBytes("%06d%s".format(i, uuid.toString.substring(0, 10))))
      put.addColumn(F1, COL1, Bytes.toBytes("%dcol1".format(i)))
      put.addColumn(F1, COL2, Bytes.toBytes("%dcol2".format(i)))
      put.addColumn(F2, COL1, Bytes.toBytes("%dcol1".format(i)))
      put.addColumn(F2, COL2, Bytes.toBytes("%dcol2".format(i)))
      puts.add(put)
      if (puts.size() == 1000) {
        table.put(puts)
        puts.clear()
      }
    }
    table.put(puts)

    table.close()
    admin.close()
    conn.close()
  }

}
