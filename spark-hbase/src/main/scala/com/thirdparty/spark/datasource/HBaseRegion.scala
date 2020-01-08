package com.thirdparty.spark.datasource

// @author https://github.com/bugboy1024
case class HBaseRegion(start: Option[Array[Byte]] = None,
                       end: Option[Array[Byte]] = None,
                       server: Option[String] = None)
