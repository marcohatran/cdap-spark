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

import org.apache.spark.ml.feature.ChiSqSelectorModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.StageConfigurer;
import co.cask.cdap.etl.api.batch.SparkCompute;
import co.cask.cdap.etl.api.batch.SparkExecutionPluginContext;
import de.kp.works.core.feature.FeatureConfig;
import de.kp.works.core.SchemaUtil;
import de.kp.works.core.feature.FeatureCompute;
import de.kp.works.core.ml.MLUtils;

@Plugin(type = SparkCompute.PLUGIN_TYPE)
@Name("ChiSquaredSelector")
@Description("A transformation stage that leverages a trained Chi-Squared Selector model to select "
		+ "categorical features to use for predicting categorical labels.")
public class ChiSquaredSelector extends FeatureCompute {

	private static final long serialVersionUID = 181964982743803324L;

	private ChiSquaredSelectorConfig config;
	private ChiSqSelectorModel model;

	public ChiSquaredSelector(ChiSquaredSelectorConfig config) {
		this.config = config;
	}

	@Override
	public void initialize(SparkExecutionPluginContext context) throws Exception {
		config.validate();
		
		ChiSquaredRecorder recorder = new ChiSquaredRecorder();
		/*
		 * STEP #1: Retrieve the trained feature model that refers 
		 * to the provide name, stage and option.  Chi-Squared models 
		 * do not have any metrics, i.e. there is no model option: 
		 * always the latest model is used
		 */
		model = recorder.read(context, config.modelName, config.modelStage, LATEST_MODEL);
		if (model == null)
			throw new IllegalArgumentException(String.format("[%s] A feature model with name '%s' does not exist.",
					this.getClass().getName(), config.modelName));

		/*
		 * STEP #2: Retrieve the profile of the trained feature 
		 * model for subsequent annotation
		 */
		profile = recorder.getProfile();

	}

	@Override
	public void configurePipeline(PipelineConfigurer pipelineConfigurer) throws IllegalArgumentException {

		config.validate();

		StageConfigurer stageConfigurer = pipelineConfigurer.getStageConfigurer();
		/*
		 * Try to determine input and output schema; if these schemas are not explicitly
		 * specified, they will be inferred from the provided data records
		 */
		inputSchema = stageConfigurer.getInputSchema();
		if (inputSchema != null) {
			
			validateSchema(inputSchema);
			/*
			 * In cases where the input schema is explicitly provided, we determine the
			 * output schema by explicitly adding the output column
			 */
			outputSchema = getArrayOutputSchema(inputSchema, config.outputCol, Schema.Type.DOUBLE);
			stageConfigurer.setOutputSchema(outputSchema);

		}

	}
	
	@Override
	public void validateSchema(Schema inputSchema) {
		config.validateSchema(inputSchema);
	}
	/**
	 * This method computes the transformed features by applying a trained
	 * Chi-Squared Selector model; as a result, the source dataset is enriched 
	 * by an extra column (outputCol) that specifies the target variable in form 
	 * of an Array[Double]
	 */
	@Override
	public Dataset<Row> compute(SparkExecutionPluginContext context, Dataset<Row> source) throws Exception {

		ChiSquaredSelectorConfig selectorConfig = (ChiSquaredSelectorConfig)config;
		/*
		 * Build internal column from input column and cast to 
		 * double vector
		 */
		Dataset<Row> vectorset = MLUtils.vectorize(source, selectorConfig.inputCol, "_input", true);
		
		model.setFeaturesCol("_input");
		model.setOutputCol("_vector");
		
		Dataset<Row> transformed = model.transform(vectorset);
		/*
		 * For compliance purposes with CDAP data schemas, we have to resolve
		 * the output format as Array Of Double
		 */
		Dataset<Row> output = MLUtils.devectorize(transformed, "_vector", config.outputCol).drop("_input").drop("_vector");
		return annotate(output, FEATURE_TYPE);

	}

	public static class ChiSquaredSelectorConfig extends FeatureConfig {

		private static final long serialVersionUID = 6059733954453473186L;
		
		public void validate() {
			super.validate();

		}
		
		public void validateSchema(Schema inputSchema) {
			super.validateSchema(inputSchema);
			
			/** INPUT COLUMN **/
			SchemaUtil.isArrayOfNumeric(inputSchema, inputCol);
			
		}
		
	}
}
