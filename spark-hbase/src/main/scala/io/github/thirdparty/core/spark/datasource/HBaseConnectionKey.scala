package io.github.thirdparty.core.spark.datasource

import java.io.IOException

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HConstants
import org.apache.hadoop.hbase.ipc.RpcControllerFactory
import org.apache.hadoop.hbase.security.{User, UserProvider}

import scala.collection.mutable

// copy from https://github.com/apache/hbase-connectors
// @author https://github.com/thirdparty-core
class HBaseConnectionKey(c: Configuration) {
  val conf: Configuration = c
  val CONNECTION_PROPERTIES: Array[String] = Array[String](
    HConstants.ZOOKEEPER_QUORUM,
    HConstants.ZOOKEEPER_ZNODE_PARENT,
    HConstants.ZOOKEEPER_CLIENT_PORT,
    HConstants.HBASE_CLIENT_PAUSE,
    HConstants.HBASE_CLIENT_RETRIES_NUMBER,
    HConstants.HBASE_RPC_TIMEOUT_KEY,
    HConstants.HBASE_META_SCANNER_CACHING,
    HConstants.HBASE_CLIENT_INSTANCE_ID,
    HConstants.RPC_CODEC_CONF_KEY,
    HConstants.USE_META_REPLICAS,
    RpcControllerFactory.CUSTOM_CONTROLLER_CONF_KEY)

  var username: String = _
  var m_properties = mutable.HashMap.empty[String, String]
  if (conf != null) {
    for (property <- CONNECTION_PROPERTIES) {
      val value: String = conf.get(property)
      if (value != null) {
        m_properties.+=((property, value))
      }
    }
    try {
      val provider: UserProvider = UserProvider.instantiate(conf)
      val currentUser: User = provider.getCurrent
      if (currentUser != null) {
        username = currentUser.getName
      }
    }
    catch {
      case e: IOException => {
        println("Error obtaining current user, skipping username in HBaseConnectionKey", e)
      }
    }
  }

  // make 'properties' immutable
  val properties: Map[String, String] = m_properties.toMap

  override def hashCode: Int = {
    val prime: Int = 31
    var result: Int = 1
    if (username != null) {
      result = username.hashCode
    }
    for (property <- CONNECTION_PROPERTIES) {
      val value: Option[String] = properties.get(property)
      if (value.isDefined) {
        result = prime * result + value.hashCode
      }
    }
    result
  }

  override def equals(obj: Any): Boolean = {
    if (obj == null) return false
    if (getClass ne obj.getClass) return false
    val that: HBaseConnectionKey = obj.asInstanceOf[HBaseConnectionKey]
    if (this.username != null && !(this.username == that.username)) {
      return false
    }
    else if (this.username == null && that.username != null) {
      return false
    }
    if (this.properties == null) {
      if (that.properties != null) {
        return false
      }
    }
    else {
      if (that.properties == null) {
        return false
      }
      var flag: Boolean = true
      for (property <- CONNECTION_PROPERTIES) {
        val thisValue: Option[String] = this.properties.get(property)
        val thatValue: Option[String] = that.properties.get(property)
        flag = true
        if (thisValue eq thatValue) {
          flag = false //continue, so make flag be false
        }
        if (flag && (thisValue == null || !(thisValue == thatValue))) {
          return false
        }
      }
    }
    true
  }

  override def toString: String = {
    "HBaseConnectionKey{" + "properties=" + properties + ", username='" + username + '\'' + '}'
  }
}
