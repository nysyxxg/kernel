package com.thirdparty.spark.datasource

// @author https://github.com/thirdparty-core
case class HBaseRegion(start: Option[Array[Byte]] = None,
                       end: Option[Array[Byte]] = None,
                       server: Option[String] = None)
