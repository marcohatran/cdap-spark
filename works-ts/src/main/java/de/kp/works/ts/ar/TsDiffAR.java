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

import de.kp.works.ts.model.DiffAutoRegressionModel;

public class TsDiffAR {

	private TsDiffARConfig config;
	private DiffAutoRegressionModel model;
	
	public TsDiffAR(TsDiffARConfig config) {
		this.config = config;
	}

	public static class TsDiffARConfig extends ARConfig {

		private static final long serialVersionUID = -8352931460177951709L;

		public void validate() {
			super.validate();
		}
		
	}

}