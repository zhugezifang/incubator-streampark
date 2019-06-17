/**
  * Copyright (c) 2019 The StreamX Project
  * <p>
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements. See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership. The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License. You may obtain a copy of the License at
  * <p>
  * http://www.apache.org/licenses/LICENSE-2.0
  * <p>
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied. See the License for the
  * specific language governing permissions and limitations
  * under the License.
  */

package com.streamxhub.spark.core

import com.streamxhub.spark.core.util.{SystemPropertyUtil, Utils}
import com.streamxhub.spark.monitor.api.HeartBeat
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SparkSession

import scala.annotation.meta.getter
import scala.collection.mutable.ArrayBuffer
/**
  *
  */
trait XSpark {

  protected final def args: Array[String] = _args

  private final var _args: Array[String] = _

  private val sparkListeners = new ArrayBuffer[String]()

  @(transient@getter)
  var sparkSession: SparkSession = _

  /**
    * 初始化，函数，可以设置 sparkConf
    *
    * @param sparkConf
    */
  def initialize(sparkConf: SparkConf): Unit = {}

  /**
    * StreamingContext 运行之后执行
    */
  def afterStarted(sc: SparkContext): Unit = {
    HeartBeat(sc).start()
  }

  /**
    * StreamingContext 停止后 程序停止前 执行
    */
  def beforeStop(sc: SparkContext): Unit = {
    HeartBeat(sc).stop()
  }

  /**
    * 处理函数
    *
    * @param sc
    */
  def handle(sc: SparkContext)

  def creatingContext(): SparkContext = {
    val sparkConf = new SparkConf()
    sparkConf.set("spark.user.args", args.mkString("|"))

    val conf = SystemPropertyUtil.get("spark.conf", "")
    conf.split("\\.").last match {
      case "properties" =>
        sparkConf.setAll(Utils.getPropertiesFromFile(conf))
      case "yml" =>
        sparkConf.setAll(Utils.getPropertiesFromYaml(conf))
      case _ => throw new IllegalArgumentException("[StreamX] Usage:properties-file error")
    }
    //for debug model
    sparkConf.get("spark.app.debug", "off") match {
      case "true" | "on" | "yes" =>
        val appName = sparkConf.get("spark.app.name")
        sparkConf.setAppName(s"[LocalDebug] $appName").setMaster("local[*]")
        sparkConf.set("spark.streaming.kafka.maxRatePerPartition", "10")
      case _ =>
    }

    initialize(sparkConf)

    val extraListeners = sparkListeners.mkString(",") + "," + sparkConf.get("spark.extraListeners", "")
    if (extraListeners != "") sparkConf.set("spark.extraListeners", extraListeners)

    sparkSession = SparkSession.builder().enableHiveSupport().config(sparkConf).getOrCreate()

    val sc = sparkSession.sparkContext
    handle(sc)
    sc
  }

  def main(args: Array[String]): Unit = {

    this._args = args

    val context = creatingContext()
    afterStarted(context)
    context.stop()
    beforeStop(context)
  }

}
