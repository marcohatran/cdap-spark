package com.suning.spark
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

import com.suning.spark.FeatureType.Type
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.{Column, DataFrame}

private[suning] trait Transformable[T] {

  /**
    * Define behavior when null or not
    *
    * @param df
    * @param categories
    * @param featureType
    * @param column
    * @return
    */
  private[suning] def whenNull(
                                df: DataFrame,
                                categories: Map[String, T],
                                featureType: FeatureType.Type
                              )(column: String): Column = {
    when(col(column).isNull, lit(null))
      .otherwise(transform(categories(column), dataType = df.schema(column)
        .dataType, featureType = featureType)(df(column)))
  }

  /**
    * Normalize dataframe
    *
    * @param df           input dataframe
    * @param keepOriginal flag to keep or replace original columns
    * @param column       target column
    * @param otherColumns other columns
    * @return transformed dataframe
    */
  def transform(
                 df: DataFrame,
                 keepOriginal: Boolean,
                 column: String,
                 otherColumns: String*
               ): DataFrame = {
    df
  }

  /**
    * Define transformation udf
    *
    * @param data        data which is used to do transformation
    * @param dataType    column data type
    * @param featureType column feature type
    * @return
    */
  protected def transform(
                           data: T,
                           dataType: DataType,
                           featureType: Type
                         ): UserDefinedFunction

}
