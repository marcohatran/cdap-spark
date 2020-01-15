package de.kp.works.ml.clustering;
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

import org.apache.spark.ml.clustering.BisectingKMeansModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import com.google.gson.Gson;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Macro;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.StageConfigurer;
import co.cask.cdap.etl.api.batch.SparkExecutionPluginContext;
import de.kp.works.core.BaseClusterConfig;
import de.kp.works.core.BaseClusterSink;

@Plugin(type = "sparksink")
@Name("BisectingKMeansSink")
@Description("A building stage for an Apache Spark based Bisecting KMeans clustering model.")
public class BisectingKMeansSink extends BaseClusterSink {
	/*
	 * Bisecting k-means is a kind of hierarchical clustering using
	 * a divisive (or “top-down”) approach: all observations start in
	 * one cluster, and splits are performed recursively as one moves
	 * down the hierarchy.
	 * 
	 * Bisecting K-means can often be much faster than regular K-means, 
	 * but it will generally produce a different clustering.
	 */
	private static final long serialVersionUID = 7306065544750480510L;

	public BisectingKMeansSink(BisectingKMeansConfig config) {
		this.config = config;
		this.className = BisectingKMeansSink.class.getName();
	}

	@Override
	public void configurePipeline(PipelineConfigurer pipelineConfigurer) throws IllegalArgumentException {
		super.configurePipeline(pipelineConfigurer);

		/* Validate configuration */
		((BisectingKMeansConfig)config).validate();

		/*
		 * Validate whether the input schema exists, contains the specified field for
		 * the feature vector and defines the feature vector as an ARRAY[DOUBLE]
		 */
		StageConfigurer stageConfigurer = pipelineConfigurer.getStageConfigurer();
		inputSchema = stageConfigurer.getInputSchema();
		if (inputSchema != null)
			validateSchema(inputSchema, config);

	}

	@Override
	public void compute(SparkExecutionPluginContext context, Dataset<Row> source) throws Exception {

		/*
		 * STEP #1: Extract parameters and train Bisecting KMeans model
		 */
		String featuresCol = config.featuresCol;
		Map<String, Object> params = config.getParamsAsMap();
		/*
		 * The vectorCol specifies the internal column that has to be built from the
		 * featuresCol and that is used for training purposes
		 */
		String vectorCol = "_vector";
		/*
		 * Prepare provided dataset by vectorizing the feature column which is specified
		 * as Array[Numeric]
		 */
		BisectingKMeansTrainer trainer = new BisectingKMeansTrainer();
		Dataset<Row> vectorset = trainer.vectorize(source, featuresCol, vectorCol);

		BisectingKMeansModel model = trainer.train(vectorset, vectorCol, params);
		/*
		 * STEP #2: Compute silhouette coefficient as metric for this Bisecting KMeans
		 * parameter setting: to this end, the predictions are computed based on the 
		 * trained model and the vectorized data set
		 */
		String predictionCol = "_cluster";
		model.setPredictionCol(predictionCol);

		Dataset<Row> predictions = model.transform(vectorset);
		/*
		 * The Clustering evaluator computes the silhouette coefficent of the computed
		 * predictions as a means to evaluate the quality of the chosen parameters
		 */
		ClusteringEvaluator evaluator = new ClusteringEvaluator();

		evaluator.setPredictionCol(predictionCol);
		evaluator.setVectorCol(vectorCol);

		evaluator.setMetricName("silhouette");
		evaluator.setDistanceMeasure("squaredEuclidean");

		Double coefficent = evaluator.evaluate(predictions);
		/*
		 * The silhouette coefficent is specified as JSON metrics for this Bisecting 
		 * KMeans model and stored by the Bisecting KMeans manager
		 */
		Map<String, Object> metrics = new HashMap<>();

		metrics.put("name", "silhouette");
		metrics.put("measure", "squaredEuclidean");
		metrics.put("coefficient", coefficent);
		/*
		 * STEP #3: Store trained Bisecting KMeans model including its associated 
		 * parameters and metrics
		 */
		String paramsJson = config.getParamsAsJSON();
		String metricsJson = new Gson().toJson(metrics);

		String modelName = config.modelName;
		new BisectingKMeansManager().save(modelFs, modelMeta, modelName, paramsJson, metricsJson, model);

	}
	
	public static class BisectingKMeansConfig extends BaseClusterConfig {

		private static final long serialVersionUID = -1120652583264276007L;
		
		@Description("The desired number of leaf clusters. Must be > 1. Default is 4.")
		@Macro
		public Integer k;

		@Description("The minimum number of points (if greater than or equal to 1.0) or the minimum proportion "
				+ "of points (if less than 1.0) of a divisible cluster. Default is 1.0.")
		@Macro
		public Double minDivisibleClusterSize;
		
	    @Description("The (maximum) number of iterations the algorithm has to execute. Default value: 20")
	    @Macro
	    private Integer maxIter;

	    public BisectingKMeansConfig() {
	    	
	    		k = 4;
	    		maxIter = 20;
	    		minDivisibleClusterSize = 1.0;
	    	
	    }

		public Map<String, Object> getParamsAsMap() {

			Map<String, Object> params = new HashMap<String, Object>();
			params.put("k", k);
			params.put("maxIter", maxIter);

			params.put("minDivisibleClusterSize", minDivisibleClusterSize);
			return params;

		}

		public void validate() {
			super.validate();

			if (k <= 1) {
				throw new IllegalArgumentException(String.format("[%s] The number of leaf clusters must be greater than 1.",
						this.getClass().getName()));
			}
			if (maxIter <= 0) {
				throw new IllegalArgumentException(String
						.format("[%s] The number of iterations must be greater than 0.", this.getClass().getName()));
			}
			if (minDivisibleClusterSize <= 0.0) {
				throw new IllegalArgumentException(String
						.format("[%s] The minimum number of points or proporation of points must be greater than to 0.0.", this.getClass().getName()));
			}
	    
		}
	}
}
