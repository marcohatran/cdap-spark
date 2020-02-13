package de.kp.works.core.ml;
/*
 * Copyright (c) 2019 Dr. Krusche & Partner PartG. All rights reserved.
 *
 * This software is the confidential and proprietary information of 
 * Dr. Krusche & Partner PartG ("Confidential Information"). 
 * 
 * You shall not disclose such Confidential Information and shall use 
 * it only in accordance with the terms of the license agreement you 
 * entered into with Dr. Krusche & Partner PartG.
 * 
 * @author Stefan Krusche, Dr. Krusche & Partner PartG
 * 
 */

import java.lang.reflect.Type;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.table.Put;
import co.cask.cdap.api.dataset.table.Table;

public class AbstractClusteringManager extends AbstractModelManager {

	protected Type metricsType = new TypeToken<Map<String, Object>>() {
	}.getType();

	protected void setMetadata(long ts, Table table, String algorithmName, String modelName, String modelParams,
			String modelMetrics, String fsPath) {
		/*
		 * Unpack recommendation metrics to build time series of metric values
		 */
		Map<String, Object> metrics = new Gson().fromJson(modelMetrics, metricsType);

		Double silhouette_euclidean = (Double) metrics.get("silhouette_euclidean");
		Double silhouette_cosine = (Double) metrics.get("silhouette_cosine");
		Double perplexity = (Double) metrics.get("perplexity");
		Double likelihood = (Double) metrics.get("likelihood");

		String fsName = SparkMLManager.RECOMMENDATION_FS;
		String modelVersion = getModelVersion(table, algorithmName, modelName);

		byte[] key = Bytes.toBytes(ts);
		table.put(new Put(key).add("timestamp", ts).add("name", modelName).add("version", modelVersion)
				.add("algorithm", algorithmName).add("params", modelParams)
				.add("silhouette_euclidean", silhouette_euclidean).add("silhouette_cosine", silhouette_cosine)
				.add("perplexity", perplexity).add("likelihood", likelihood).add("fsName", fsName)
				.add("fsPath", fsPath));

	}

}