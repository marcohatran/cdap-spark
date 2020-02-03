package de.kp.works.ml.regression;
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

import java.util.HashMap;
import java.util.Map;

import org.apache.spark.ml.regression.AFTSurvivalRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import com.google.common.base.Strings;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Macro;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.StageConfigurer;
import co.cask.cdap.etl.api.batch.SparkExecutionPluginContext;

import de.kp.works.core.RegressorConfig;
import de.kp.works.core.RegressorSink;

import de.kp.works.core.ml.RegressorEvaluator;

@Plugin(type = "sparksink")
@Name("AFTSurvivalRegressor")
@Description("A building stage for an Apache Spark based AFT survival regressor model.")
public class AFTSurvivalRegressor extends RegressorSink {

	private static final long serialVersionUID = -2096945742865221471L;
	
	public AFTSurvivalRegressor(AFTSurvivalConfig config) {
		this.config = config;
		this.className = AFTSurvivalRegressor.class.getName();
	}

	@Override
	public void configurePipeline(PipelineConfigurer pipelineConfigurer) throws IllegalArgumentException {
		super.configurePipeline(pipelineConfigurer);

		/* Validate configuration */
		((AFTSurvivalConfig)config).validate();
		
		/* Validate schema */
		StageConfigurer stageConfigurer = pipelineConfigurer.getStageConfigurer();
		inputSchema = stageConfigurer.getInputSchema();
		if (inputSchema != null)
			validateSchema(inputSchema, config);

	}
	
	@Override
	public void compute(SparkExecutionPluginContext context, Dataset<Row> source) throws Exception {
		
		AFTSurvivalConfig regressorConfig = (AFTSurvivalConfig)config;
		/*
		 * STEP #1: Extract parameters and train regression model
		 */
		String featuresCol = regressorConfig.featuresCol;
		String labelCol = regressorConfig.labelCol;

		String censorCol = regressorConfig.censorCol;
		
		Map<String, Object> params = regressorConfig.getParamsAsMap();
		String paramsJson = regressorConfig.getParamsAsJSON();
		/*
		 * The vectorCol specifies the internal column that has
		 * to be built from the featuresCol and that is used for
		 * training purposes
		 */
		String vectorCol = "_vector";
		/*
		 * Prepare provided dataset by vectorizing the feature
		 * column which is specified as Array[Numeric]
		 */
		AFTSurvivalTrainer trainer = new AFTSurvivalTrainer();
		Dataset<Row> vectorset = trainer.vectorize(source, featuresCol, vectorCol);
		/*
		 * Split the vectorset into a train & test dataset for
		 * later regression evaluation
		 */
	    Dataset<Row>[] splitted = vectorset.randomSplit(regressorConfig.getSplits());
		
	    Dataset<Row> trainset = splitted[0];
	    Dataset<Row> testset = splitted[1];

	    AFTSurvivalRegressionModel model = trainer.train(trainset, vectorCol, labelCol, censorCol, params);
		/*
		 * STEP #2: Evaluate regression model and compute
		 * approved list of metrics
		 */
	    String predictionCol = "_prediction";
	    model.setPredictionCol(predictionCol);

	    Dataset<Row> predictions = model.transform(testset);	    
	    String metricsJson = RegressorEvaluator.evaluate(predictions, labelCol, predictionCol);
		/*
		 * STEP #3: Store trained regression model including
		 * its associated parameters and metrics
		 */		
		String modelName = regressorConfig.modelName;
		new AFTSurvivalRegressorManager().save(modelFs, modelMeta, modelName, paramsJson, metricsJson, model);

	}

	@Override 
	public void validateSchema(Schema inputSchema, RegressorConfig config) {
		super.validateSchema(inputSchema, config);
		
		/** CENSOR COL **/

		AFTSurvivalConfig survivalConfig = (AFTSurvivalConfig)config;
		
		Schema.Field censorCol = inputSchema.getField(survivalConfig.labelCol);
		if (censorCol == null) {
			throw new IllegalArgumentException(String
					.format("[%s] The input schema must contain the field that defines the censor value.", className));
		}

		Schema.Type censorType = censorCol.getSchema().getType();
		/*
		 * The censor must be a numeric data type (double, float, int, long), which then
		 * is casted to Double (see regression trainer)
		 */
		if (isNumericType(censorType) == false) {
			throw new IllegalArgumentException("The data type of the censor field must be numeric.");
		}
		
	}
	
	public static class AFTSurvivalConfig extends RegressorConfig {

		private static final long serialVersionUID = 8618207399826721560L;

		@Description("The name of the field in the input schema that contains the censor value. The censor value "
				+ "can be 0 or 1. If the value is 1, it means the event has occurred (uncensored); otherwise censored.")
		@Macro
		public String censorCol;

		@Description("The maximum number of iterations to train the AFT Survival Regression model. Default is 100.")
		@Macro
		public Integer maxIter;

		@Description("The positive convergence tolerance of iterations. Smaller values will lead to higher accuracy with the cost "
				+ "of more iterations. Default is 1e-6")
		@Macro
		public Double tol;		
		
		/*
		 * Quantiles and associated column are not externalized, i.e. Apache Spark's
		 * default settings are used 
		 */
		
		public AFTSurvivalConfig() {

			dataSplit = "70:30";
			
			maxIter = 100;
			tol = 1e-6;
			
		}
	    
		@Override
		public Map<String, Object> getParamsAsMap() {
			
			Map<String, Object> params = new HashMap<>();

			params.put("maxIter", maxIter);
			params.put("tol", tol);

			params.put("dataSplit", dataSplit);
			return params;
		
		}

		public void validate() {
			super.validate();

			/** MODEL & COLUMNS **/
			if (Strings.isNullOrEmpty(censorCol)) {
				throw new IllegalArgumentException(
						String.format("[%s] The name of the field that contains the censor value must not be empty.",
								this.getClass().getName()));
			}
			
			/** PARAMETERS **/
			if (maxIter < 1)
				throw new IllegalArgumentException(String.format(
						"[%s] The maximum number of iterations must be at least 1.", this.getClass().getName()));

			if (tol <= 0D)
				throw new IllegalArgumentException(
						String.format("[%s] The iteration tolerance must be positive.", this.getClass().getName()));			
			
		}
		
	}
}
