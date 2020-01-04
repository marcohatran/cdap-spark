package de.kp.works.ml.classification;

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

import java.io.IOException;
import java.util.Date;

import org.apache.spark.ml.classification.*;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.FileSet;
import co.cask.cdap.api.dataset.table.Put;
import co.cask.cdap.api.dataset.table.Table;
import de.kp.works.ml.AbstractModelManager;

public class DecisionTreeClassifierManager extends AbstractModelManager {

	private String ALGORITHM_NAME = "DecisionTreeClassifier";

	public DecisionTreeClassificationModel read(Table table, FileSet fs, String modelName) throws IOException {
		
		String fsPath = getModelFsPath(table, ALGORITHM_NAME, modelName);
		if (fsPath == null) return null;
		/*
		 * Leverage Apache Spark mechanism to read the DecisionTreeClassification model
		 * from a model specific file set
		 */
		String modelPath = fs.getBaseLocation().append(fsPath).toURI().getPath();
		return DecisionTreeClassificationModel.load(modelPath);
		
	}

	public void save(Table table, FileSet fs, String fsName, String modelName, String modelParams, String modelMetrics,
			DecisionTreeClassificationModel model) throws IOException {

		/***** MODEL COMPONENTS *****/

		/*
		 * Define the path of this model on CDAP's internal classification fileset
		 */
		Long ts = new Date().getTime();
		String fsPath = ALGORITHM_NAME + "/" + ts.toString() + "/" + modelName;
		/*
		 * Leverage Apache Spark mechanism to write the DecisionTreeClassifier model
		 * to a model specific file set
		 */
		String modelPath = fs.getBaseLocation().append(fsPath).toURI().getPath();
		model.save(modelPath);

		/***** MODEL METADATA *****/

		/*
		 * Append model metadata to the metadata table associated with the
		 * classification fileset
		 */
		String modelVersion = getModelVersion(table, ALGORITHM_NAME, modelName);

		byte[] key = Bytes.toBytes(ts);
		table.put(new Put(key).add("timestamp", ts).add("name", modelName).add("version", modelVersion)
				.add("algorithm", ALGORITHM_NAME).add("params", modelParams).add("metrics", modelMetrics)
				.add("fsName", fsName).add("fsPath", fsPath));

	}

}
