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

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Macro;
import de.kp.works.ts.params.ModelParams;

public class ARSinkConfig extends ARConfig {

	private static final long serialVersionUID = 4853654573657359481L;

	@Description(ModelParams.REG_PARAM_DESC)
	@Macro
	public Double regParam;

	@Description(ModelParams.ELASTIC_NET_PARAM_DESC)
	@Macro
	public Double elasticNetParam;

	@Description(ModelParams.STANDARDIZATION_DESC)
	@Macro
	public String standardization;

	@Description(ModelParams.FIT_INTERCEPT_DESC)
	@Macro
	public String fitIntercept;

	@Description("The split of the dataset into train & test data, e.g. 80:20. Default is 70:30")
	@Macro
	public String dataSplit;

	public void validate() {
		super.validate();

		if (elasticNetParam < 0D || elasticNetParam > 1D)
			throw new IllegalArgumentException(String.format(
					"[%s] The ElasticNet mixing parameter must be in interval [0, 1].", this.getClass().getName()));

		if (regParam < 0D)
			throw new IllegalArgumentException(String
					.format("[%s] The regularization parameter must be at least 0.0.", this.getClass().getName()));
		
	}
	
	public double[] getSplits() {
		return getDataSplits(dataSplit);
	}

}
