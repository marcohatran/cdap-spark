package de.kp.works.ts
/*
 * Copyright (c) 2019 Dr. Krusche & Partner PartG. All rights reserved.
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
 * 
 * @author Stefan Krusche, Dr. Krusche & Partner PartG
 * 
 */

import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.ml.param._

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.util._

import org.apache.spark.sql._
import org.apache.spark.sql.expressions.{Window, WindowSpec}

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

import scala.collection.mutable.WrappedArray

case class LabeledPoint(
    features: Vector,
    label: Double
)

trait TimeLaggingParams extends TimeParams {
  
  final val featuresCol = new Param[String](TimeLaggingParams.this, "featuresCol",
      "Name of the column that contains the feature values", (value:String) => true)
  
  final val labelCol = new Param[String](TimeLaggingParams.this, "labelCol",
      "Name of the column that contains the label value", (value:String) => true)
    
  final val lag = new Param[Int](TimeLaggingParams.this, "lag",
      "The number of past points of time to take into account for vectorization.", (value:Int) => true)

  final val laggingType = new Param[String](this, "laggingType",
    "The selector type of the lagging algorithm. Supported values are: 'features', 'pastFeatures' and 'featuresAndLabels'.",
    ParamValidators.inArray[String](Array("features", "pastFeatures", "featuresAndLabels")))
      
  /** @group setParam */
  def setFeaturesCol(value:String): this.type = set(featuresCol, value)
 
  /** @group setParam */
  def setLabelCol(value:String): this.type = set(labelCol, value)
 
  /** @group setParam */
  def setLag(value:Int): this.type = set(lag, value)
 
  /** @group setParam */
  def setLaggingType(value:String): this.type = set(laggingType, value)
      
  setDefault(featuresCol -> "features", labelCol -> "label", lag -> 10, laggingType -> "featuresAndLabels")
  
  override def validateSchema(schema:StructType):Unit = {
    super.validateSchema(schema)
    
  }

}
/*
 * Lagging: vector of past N values
 * 
 * The goal of this transformer is to prepare (cleaned) time series data for (demand) prediction:
 * 
 * For each value x(t) of the time series, we build the vector x(t-N), ..., x(t-2), x(t-1), x(t). 
 * Weuse the past values x(t-N), ..., x(t-2), x(t-1) as feature vector for the prediction model 
 * and the current value x(t) as the target column or label to train the model.
 * 
 * REMINDER: Build the vector of past N values after partitioning the dataset into a training set
 * and a test set in order to avoid data leakage from neighboring values.
 */
class Lagging(override val uid: String) extends Transformer with TimeLaggingParams {
  
  def this() = this(Identifiable.randomUID("lagging"))
 
  def transform(dataset:Dataset[_]):DataFrame = {
    
    validateSchema(dataset.schema)
    /*
     * This transformer operates on a TimestampType column;
     * we ensure that the time column is formatted as a time
     * stamp.
     * 
     * For further processing, we additionally have to turn
     * the time column into a (reversible) LongType
     */
    val timeset = createTimeset(dataset)
    val k = $(lag)

    if ($(laggingType) == "featuresAndLabels") {      
      /* 
       * Specify a window with lag + 1 values as we want to take
       * feature vector (lag) and target value (1) into account
       */
      val spec = Window.partitionBy().rowsBetween(-k, 0)
      /*
       * User defined function to transform lag window into
       * a labeled point
       */
      val buildLabelPoint = buildLabelFeatures_udf(k)

      val result = timeset
        /* Transform into (lag + 1) vectors */ 
        .withColumn("_vector", collect_list(col($(valueCol))).over(spec))
        /* 
         * Build labeled point and remove all null values; null indicates
         * that the length of the feature vector is smaller than 'lag'
         */
        .withColumn("_lp", buildLabelPoint(col("_vector"))).filter(col("_lp").isNotNull)
        /*
         * Split labeled point column int features & label column
         */
        .withColumn($(featuresCol), col("_lp").getItem("features"))
        .withColumn($(labelCol), col("_lp").getItem("label"))
        /*
         * Remove internal columns
         */
        .drop("_lp").drop("_vector")
        
      result
    
    } else {
      /* 
       * Specify a window with lag values as we only want to
       * take feature vector (lag) into account
       */
      val spec = if ($(laggingType) == "pastFeatures")
        /*
         * In this case, we exclude the current point in time;
         * this is implemented to build testsets for model
         * evaluation without data leakage
         */
        Window.partitionBy().rowsBetween(-k, -1)
      
      else
        /*
         * This case is for predicting purposes to retrieve
         * the next point in time that follows the current
         * one
         */
        Window.partitionBy().rowsBetween(-(k-1), 0)
        
      /*
       * User defined function to transform lag window into
       * a feature vector
       */
      val buildFeatures = buildFeatures_udf(k)
      
      val result = timeset
        /* Transform into (lag) vectors */ 
        .withColumn("_vector", collect_list(col($(valueCol))).over(spec))
        /* 
         * Build features and remove all null values; null indicates
         * that the length of the feature vector is smaller than 'lag'
         */
        .withColumn($(featuresCol), buildFeatures(col("_vector"))).filter(col($(featuresCol)).isNotNull)
        /*
         * Remove internal column
         */
        .drop("_vector")
        
      result
      
    }
  }
    
  private def buildFeatures_udf(k:Int) = udf {
      vector:WrappedArray[Double] => {
        if (vector.size == k) Vectors.dense(vector.toArray) else null
      }
  }

  private def buildLabelFeatures_udf(k:Int) = udf {
      vector:WrappedArray[Double] => {
        
        if (vector.size == k + 1)
          LabeledPoint(features = Vectors.dense(vector.init.toArray), label = vector.last)
        else
          null
 
      }
    
  }
  
  override def transformSchema(schema:StructType):StructType = {    
    schema    
  }

  override def copy(extra:ParamMap):Lagging = defaultCopy(extra)
  
}