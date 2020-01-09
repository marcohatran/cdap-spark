package de.kp.works.ml.prediction;
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

import org.apache.spark.ml.classification.MultilayerPerceptronClassificationModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import com.google.common.base.Strings;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.StageConfigurer;
import co.cask.cdap.etl.api.batch.SparkCompute;
import co.cask.cdap.etl.api.batch.SparkExecutionPluginContext;
import de.kp.works.core.BasePredictorCompute;
import de.kp.works.core.BasePredictorConfig;
import de.kp.works.core.ml.SparkMLManager;
import de.kp.works.ml.MLUtils;
import de.kp.works.ml.classification.MLPClassifierManager;


@Plugin(type = SparkCompute.PLUGIN_TYPE)
@Name("MLPPredictor")
@Description("A prediction stage that leverages a trained Apache Spark based Multilayer Perceptron classifier model.")
public class MLPPredictor extends BasePredictorCompute {

	private static final long serialVersionUID = -7257380193353979238L;

	private MLPPredictorConfig config;

	private MultilayerPerceptronClassificationModel classifier;

	public MLPPredictor(MLPPredictorConfig config) {
		this.config = config;
	}

	@Override
	public void initialize(SparkExecutionPluginContext context) throws Exception {
		config.validate();

		modelFs = SparkMLManager.getClassificationFS(context);
		modelMeta = SparkMLManager.getClassificationMeta(context);

		classifier = new MLPClassifierManager().read(modelFs, modelMeta, config.modelName);
		if (classifier == null)
			throw new IllegalArgumentException(String.format("[%s] A classifier model with name '%s' does not exist.",
					this.getClass().getName(), config.modelName));

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
			/*
			 * In cases where the input schema is explicitly provided, we determine the
			 * output schema by explicitly adding the prediction column
			 */
			outputSchema = getOutputSchema(inputSchema, config.predictionCol);
			stageConfigurer.setOutputSchema(outputSchema);

		}

	}

	/**
	 * This method computes predictions either by applying a trained Multilayer
	 * Perceptron classification model; as a result, the source dataset is enriched
	 * by an extra column (predictionCol) that specifies the target variable in form
	 * of a Double value
	 */
	@Override
	public Dataset<Row> compute(SparkExecutionPluginContext context, Dataset<Row> source) throws Exception {
		/*
		 * STEP #1: Extract configuration parameters
		 */
		String featuresCol = config.featuresCol;
		String predictionCol = config.predictionCol;
		/*
		 * The vectorCol specifies the internal column that has to be built from the
		 * featuresCol and that is used for prediction purposes
		 */
		String vectorCol = "_vector";
		/*
		 * Prepare provided dataset by vectorizing the feature column which is specified
		 * as Array[Double]
		 */
		Dataset<Row> vectorset = MLUtils.vectorize(source, featuresCol, vectorCol);

		classifier.setFeaturesCol(vectorCol);
		classifier.setPredictionCol(predictionCol);

		Dataset<Row> predictions = classifier.transform(vectorset);

		Dataset<Row> output = predictions.drop(vectorCol);
		return output;

	}

	public static class MLPPredictorConfig extends BasePredictorConfig {

		private static final long serialVersionUID = -3792791640714779280L;

		public void validate() {

			/** MODEL & COLUMNS **/
			if (!Strings.isNullOrEmpty(modelName)) {
				throw new IllegalArgumentException(
						String.format("[%s] The model name must not be empty.", this.getClass().getName()));
			}
			if (!Strings.isNullOrEmpty(featuresCol)) {
				throw new IllegalArgumentException(
						String.format("[%s] The name of the field that contains the feature vector must not be empty.",
								this.getClass().getName()));
			}
			if (!Strings.isNullOrEmpty(predictionCol)) {
				throw new IllegalArgumentException(String.format(
						"[%s] The name of the field that contains the predicted label value must not be empty.",
						this.getClass().getName()));
			}

		}
	}

}