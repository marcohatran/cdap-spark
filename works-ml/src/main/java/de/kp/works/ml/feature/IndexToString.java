package de.kp.works.ml.feature;
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

import java.util.ArrayList;
import java.util.List;

import org.apache.spark.ml.feature.StringIndexerModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.etl.api.batch.SparkCompute;
import co.cask.cdap.etl.api.batch.SparkExecutionPluginContext;
import de.kp.works.core.BaseFeatureCompute;
import de.kp.works.core.BaseFeatureConfig;
import de.kp.works.core.ml.SparkMLManager;
import de.kp.works.ml.feature.StringToIndex.StringToIndexConfig;

@Plugin(type = SparkCompute.PLUGIN_TYPE)
@Name("IndexToString")
@Description("A transformation stage that leverages the Apache Spark IndexToString based on a trained StringIndexer model.")
public class IndexToString extends BaseFeatureCompute {

	private static final long serialVersionUID = -7894198310242025849L;

	private StringIndexerModel model;

	public IndexToString(IndexToStringConfig config) {
		this.config = config;
	}

	@Override
	public void initialize(SparkExecutionPluginContext context) throws Exception {
		((StringToIndexConfig)config).validate();

		modelFs = SparkMLManager.getFeatureFS(context);
		modelMeta = SparkMLManager.getFeatureMeta(context);

		model = new StringIndexerManager().read(modelFs, modelMeta, config.modelName);
		if (model == null)
			throw new IllegalArgumentException(String.format("[%s] A feature model with name '%s' does not exist.",
					this.getClass().getName(), config.modelName));

	}
	
	@Override
	public void validateSchema(Schema inputSchema, BaseFeatureConfig config) {
		super.validateSchema(inputSchema, config);
		
		/** INPUT COLUMN **/
		isNumeric(config.inputCol);
		
	}

	/**
	 * A helper method to compute the output schema in that use cases where an input
	 * schema is explicitly given
	 */
	public Schema getOutputSchema(Schema inputSchema, String outputField) {

		List<Schema.Field> fields = new ArrayList<>(inputSchema.getFields());
		
		fields.add(Schema.Field.of(outputField, Schema.of(Schema.Type.STRING)));
		return Schema.recordOf(inputSchema.getRecordName() + ".transformed", fields);

	}	
	/**
	 * This method computes the transformed features by applying a trained
	 * StringIndexer model; as a result, the source dataset is enriched by
	 * an extra column (outputCol) that specifies the target variable in 
	 * form of a Double
	 */
	@Override
	public Dataset<Row> compute(SparkExecutionPluginContext context, Dataset<Row> source) throws Exception {
		/*
		 * Transformation from [Numeric] to [String]
		 */
		String[] labels = model.labels();
		
		org.apache.spark.ml.feature.IndexToString transformer = new org.apache.spark.ml.feature.IndexToString();
		transformer.setInputCol(config.inputCol);
		transformer.setOutputCol(config.outputCol);

		transformer.setLabels(labels);

		Dataset<Row> output = transformer.transform(source);
		return output;

	}

	public static class IndexToStringConfig extends BaseFeatureConfig {

		private static final long serialVersionUID = -514363427339189006L;

		public void validate() {
			super.validate();

		}
		
	}
}
