package de.kp.works.ts.ar;
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

import co.cask.cdap.etl.api.batch.SparkPluginContext;

import de.kp.works.core.TimeSink;
import de.kp.works.core.ml.SparkMLManager;

public class ARSink extends TimeSink {

	private static final long serialVersionUID = -2627863312885407890L;

	@Override
	public void prepareRun(SparkPluginContext context) throws Exception {
		/*
		 * Time series model components and metadata are persisted in a CDAP FileSet
		 * as well as a Table; at this stage, we have to make sure that these internal
		 * metadata structures are present
		 */
		SparkMLManager.createTimeseriesIfNotExists(context);
		/*
		 * Retrieve time series specified dataset for later use incompute
		 */
		modelFs = SparkMLManager.getTimeseriesFS(context);
		modelMeta = SparkMLManager.getTimeseriesMeta(context);
		
	}

}