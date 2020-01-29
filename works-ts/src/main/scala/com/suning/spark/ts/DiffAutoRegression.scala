package com.suning.spark.ts
/*
 * Copyright (c) 2016 Suning R&D. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import com.suning.spark.regression.LinearRegression
import com.suning.spark.ts.TimeSeriesUtil._
import com.suning.spark.util.{Identifiable, Model, SaveLoad}

import org.apache.spark.SparkContext
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
  
class DiffAutoRegression(override val uid: String, inputCol: String, timeCol: String,
                         p: Int, d: Int,
                         regParam: Double, standardization: Boolean, elasticNetParam: Double,
                         withIntercept: Boolean)
  extends TSModel(uid, inputCol, timeCol) {

  def this(inputCol: String, timeCol: String, p: Int, d: Int, regParam: Double,
           standardization: Boolean = true, elasticNetParam: Double,
           withIntercept: Boolean = false) =
    this(Identifiable.randomUID("DiffAutoRegression"), inputCol, timeCol, p, d, regParam,
      standardization, elasticNetParam, withIntercept)

  private var lr_ar: LinearRegression = _

  //  private val lr_ar = new SparkLR().setRegParam(0.0)
  //    .setFitIntercept(false)
  //    .setStandardization(withIntercept)
  //    .setElasticNetParam(0.0)
  //    .setMaxIter(100)
  //    .setTol(1E-6)


  override def fitImpl(df: DataFrame): this.type = {

    require(p > 0 && d > 0, s"p and d can not be 0")

    val newDF = TimeSeriesUtil.DiffCombination(df, inputCol, timeCol, p, d)
    newDF.show(10)

    val r = 1 to p toArray

    val features = r.map(inputCol + "_diff_" + d + "_lag_" + _)

    val label = inputCol + "_diff_" + d + "_lag_0"


    val maxIter = 1000
    val tol = 1E-6

    newDF.persist()

    lr_ar = LinearRegression(features, label, regParam, withIntercept, standardization,
      elasticNetParam, maxIter, tol)

    lr_ar.fit(newDF)
    newDF.unpersist()

    this
  }

  def getFeatureCols:Array[String] = {

    val features = (1 to p).map(inputCol + "_diff_" + d + "_lag_" + _).toArray
    features
    
  }
  /*
   * __KUP__ We externalize the prepration steps
   * to enable model prediction from reloaded model
   */  
  def prepareDiffAR(df: DataFrame): DataFrame = {
    require(p > 0 && d > 0, s"p and d can not be 0")
    TimeSeriesUtil.DiffCombination(df, inputCol, timeCol, p, d, false)
  }
  
  override def transformImpl(df: DataFrame): DataFrame = {

    val newDF = prepareDiffAR(df)
    lr_ar.transform(newDF)

  }

  override def forecast(df: DataFrame, numAhead: Int): List[Double] = {
    require(p > 0 && d > 0, s"p and d can not be 0")

    if (lr_ar == null) fit(df)

    val newDF = transform(df).orderBy(desc(timeCol))

    val diff = "_diff_" + d
    val lag = "_lag_"
    var listDiff = newDF.select(inputCol + diff + lag + 0)
      .limit(p).collect().map(_.getDouble(0)).toList

    var listPrev = List[Double](
      getDouble(newDF.select(inputCol).limit(1).collect()(0).get(0))
    )

    val weights = getWeights()
    val intercept = getIntercept()

    (0 until numAhead).foreach {
      j => {
        val vec = Vectors.dense(listDiff.slice(0, p).toArray)
        var diff = 0.0
        (0 until p).foreach(
          i => {
            diff += vec(i) * weights(i)
          }
        )
        diff = diff + intercept

        listDiff = diff :: listDiff
        listPrev = (diff + listPrev(0)) :: listPrev
      }
    }
    listPrev.reverse.tail
  }


  def getIntercept(): Double = {
    lr_ar.getIntercept()
  }

  def getWeights(): Vector = {
    lr_ar.getWeights()
  }

  override def copy(): Model = {

    new DiffAutoRegression(inputCol, timeCol, p, d, regParam, standardization, elasticNetParam,
      withIntercept)
  }

  override def save(path: String): Unit = {
    DiffAutoRegression.save(this, path)
  }

  override def saveHDFS(sc: SparkContext, path: String): Unit = {
    DiffAutoRegression.saveHDFS(sc, this, path)
  }
}

object DiffAutoRegression extends SaveLoad[DiffAutoRegression] {
  def apply(uid: String, inputCol: String,
            timeCol: String, maxDiff: Int, diff: Int, regParam: Double, standardization: Boolean,
            elasticNetParam: Double, withIntercept: Boolean):
  DiffAutoRegression = new DiffAutoRegression(uid, inputCol, timeCol, maxDiff, diff, regParam,
    standardization, elasticNetParam, withIntercept)

  def apply(inputCol: String, timeCol: String, maxDiff: Int, diff: Int, regParam: Double,
            standardization: Boolean, elasticNetParam: Double, withIntercept: Boolean):
  DiffAutoRegression = new DiffAutoRegression(inputCol, timeCol, maxDiff, diff, regParam,
    standardization, elasticNetParam, withIntercept)
}
