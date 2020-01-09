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

import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import com.google.common.base.Strings;
import com.google.gson.Gson;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Macro;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.StageConfigurer;
import co.cask.cdap.etl.api.batch.SparkExecutionPluginContext;
import de.kp.works.core.BaseRegressorConfig;
import de.kp.works.core.BaseRegressorSink;

@Plugin(type = "sparksink")
@Name("LinearRegressor")
@Description("A building stage for an Apache Spark based linear regressor model.")
public class LinearRegressor extends BaseRegressorSink {

	private static final long serialVersionUID = -9189352556712045343L;
	
	public LinearRegressor(LinearConfig config) {
		this.config = config;
		this.className = LinearRegressor.class.getName();
	}

	@Override
	public void configurePipeline(PipelineConfigurer pipelineConfigurer) throws IllegalArgumentException {
		super.configurePipeline(pipelineConfigurer);

		/* Validate configuration */
		config.validate();
		
		/* Validate schema */
		StageConfigurer stageConfigurer = pipelineConfigurer.getStageConfigurer();
		inputSchema = stageConfigurer.getInputSchema();
		if (inputSchema != null)
			validateSchema(inputSchema, config);

	}
	
	@Override
	public void compute(SparkExecutionPluginContext context, Dataset<Row> source) throws Exception {
		/*
		 * STEP #1: Extract parameters and train regression model
		 */
		String featuresCol = config.featuresCol;
		String labelCol = config.labelCol;

		Map<String, Object> params = config.getParamsAsMap();
		/*
		 * The vectorCol specifies the internal column that has
		 * to be built from the featuresCol and that is used for
		 * training purposes
		 */
		String vectorCol = "_vector";
		/*
		 * Prepare provided dataset by vectorizing the feature
		 * column which is specified as Array[Double]
		 */
		LinearTrainer trainer = new LinearTrainer();
		Dataset<Row> vectorset = trainer.vectorize(source, featuresCol, vectorCol);
		/*
		 * Split the vectorset into a train & test dataset for
		 * later regression evaluation
		 */
	    Dataset<Row>[] splitted = vectorset.randomSplit(config.getSplits());
		
	    Dataset<Row> trainset = splitted[0];
	    Dataset<Row> testset = splitted[1];

	    LinearRegressionModel model = trainer.train(trainset, vectorCol, labelCol, params);
		/*
		 * STEP #2: Compute accuracy of the trained regression
		 * model
		 */
	    String predictionCol = "_prediction";
	    model.setPredictionCol(predictionCol);

	    Dataset<Row> predictions = model.transform(testset);

	    RegressionEvaluator evaluator = new RegressionEvaluator();
	    evaluator.setLabelCol(labelCol);
	    evaluator.setPredictionCol(predictionCol);
	    
	    String metricName = "rmse";
	    evaluator.setMetricName(metricName);
	    
	    double accuracy = evaluator.evaluate(predictions);
		/*
		 * The accuracy coefficent is specified as JSON metrics for
		 * this regression model and stored by the DTRegressorManager
		 */
		Map<String,Object> metrics = new HashMap<>();
		
		metrics.put("name", metricName);
		metrics.put("coefficient", accuracy);
		/*
		 * STEP #3: Store trained regression model including its associated
		 * parameters and metrics
		 */
		String paramsJson = config.getParamsAsJSON();
		String metricsJson = new Gson().toJson(metrics);
		
		String modelName = config.modelName;
		new LinearRegressorManager().save(modelFs, modelMeta, modelName, paramsJson, metricsJson, model);

	}

	public static class LinearConfig extends BaseRegressorConfig {

		private static final long serialVersionUID = -1317889959730192346L;

		@Description("The maximum number of iterations to train the Linear Regression model. Default is 100.")
		@Macro
		public Integer maxIter;

		@Description("The ElasticNet mxing parameter. For value = 0.0, the penalty is an L2 penalty."
				+ "For value = 1.0, it is an L1 penalty. For 0.0 < value < 1.0, the penalty is a combination of "
				+ "L1 and L2. Default is 0.0.")

		@Macro
		public Double elasticNetParam;

		@Description("The nonnegative regularization parameter. Default is 0.0.")
		@Macro
		public Double regParam;

		@Description("The positive convergence tolerance of iterations. Smaller values will lead to higher accuracy with the cost "
				+ "of more iterations. Default is 1e-6")
		@Macro
		public Double tol;		
		
		@Description("The solver algorithm for optimization. Supported options are 'auto', 'normal' and 'l-bfgs'. Default is 'auto'.")
		@Macro
		public String solver;
		
		public LinearConfig() {

			dataSplit = "70:30";
			maxIter = 100;

			elasticNetParam = 0D;
			regParam = 0D;
			tol = 1e-6;
			
			solver = "auto";
			
		}
	    
		@Override
		public Map<String, Object> getParamsAsMap() {
			
			Map<String, Object> params = new HashMap<>();
			params.put("maxIter", maxIter);

			params.put("elasticNetParam", elasticNetParam);
			params.put("regParam", regParam);

			params.put("tol", tol);
			params.put("solver", solver);
			
			return params;
		
		}

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
			if (!Strings.isNullOrEmpty(labelCol)) {
				throw new IllegalArgumentException(
						String.format("[%s] The name of the field that contains the label value must not be empty.",
								this.getClass().getName()));
			}

			/** PARAMETERS **/
			if (maxIter < 1)
				throw new IllegalArgumentException(String.format(
						"[%s] The maximum number of iterations must be at least 1.", this.getClass().getName()));

			if (elasticNetParam < 0D || elasticNetParam > 1D)
				throw new IllegalArgumentException(String.format(
						"[%s] The ElasticNet mixing parameter must be in interval [0, 1].", this.getClass().getName()));

			if (regParam < 0D)
				throw new IllegalArgumentException(String
						.format("[%s] The regularization parameter must be at least 0.0.", this.getClass().getName()));

			if (tol <= 0D)
				throw new IllegalArgumentException(
						String.format("[%s] The iteration tolerance must be positive.", this.getClass().getName()));

		}
				
	}

}