package de.kp.works.text.topic;
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

import java.util.ArrayList;
import java.util.List;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Macro;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.StageConfigurer;
import co.cask.cdap.etl.api.batch.SparkCompute;
import co.cask.cdap.etl.api.batch.SparkExecutionPluginContext;
import de.kp.works.core.BaseCompute;

@Plugin(type = SparkCompute.PLUGIN_TYPE)
@Name("Topic")
@Description("An tagging stage that leverages a trained Topic model to either determine the topic distribution "
		+ "per document or term-distribution per topic.")
public class Topic extends BaseCompute {

	private static final long serialVersionUID = 6494628611665323901L;

	private TopicConfig config;
	private LDATopicModel model;
	
	public Topic(TopicConfig config) {
		this.config = config;
	}

	@Override
	public void initialize(SparkExecutionPluginContext context) throws Exception {
		config.validate();

		model = new TopicManager().read(context, config.modelName);
		if (model == null)
			throw new IllegalArgumentException(
					String.format("[%s] A Topic model with name '%s' does not exist.",
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
			validateSchema(inputSchema);
			/*
			 * In cases where the input schema is explicitly provided, we determine the
			 * output schema by explicitly adding the prediction column
			 */
			outputSchema = getOutputSchema(inputSchema);
			stageConfigurer.setOutputSchema(outputSchema);

		}

	}
	@Override
	public Dataset<Row> compute(SparkExecutionPluginContext context, Dataset<Row> source) throws Exception {
		
		if (config.topicStrategy.equals("document-topic")) {
			return model.transform(source);

		} else {
			return model.topics();
		}

	}

	/**
	 * A helper method to compute the output schema in that use cases where an input
	 * schema is explicitly given
	 */
	public Schema getOutputSchema(Schema inputSchema) {
		
		if (config.topicStrategy.equals("document-topic")) {

			List<Schema.Field> fields = new ArrayList<>(inputSchema.getFields());

			fields.add(Schema.Field.of("topics", Schema.arrayOf(Schema.of(Schema.Type.INT))));
			fields.add(Schema.Field.of("weights", Schema.arrayOf(Schema.of(Schema.Type.DOUBLE))));

			return Schema.recordOf(inputSchema.getRecordName() + ".predicted", fields);
	    
		} else {

			List<Schema.Field> fields = new ArrayList<>();
			
			fields.add(Schema.Field.of("topic", Schema.of(Schema.Type.INT)));
			fields.add(Schema.Field.of("terms", Schema.arrayOf(Schema.of(Schema.Type.STRING))));
			fields.add(Schema.Field.of("weights", Schema.arrayOf(Schema.of(Schema.Type.DOUBLE))));
			
			return Schema.recordOf(inputSchema.getRecordName() + ".predicted", fields);
		
		}

	}

	@Override
	public void validateSchema(Schema inputSchema) {

		/** TEXT COLUMN **/

		Schema.Field textCol = inputSchema.getField(config.textCol);
		if (textCol == null) {
			throw new IllegalArgumentException(
					String.format("[%s] The input schema must contain the field that defines the text document.",
							this.getClass().getName()));
		}

		isString(config.textCol);

	}

	public static class TopicConfig extends BaseTopicConfig {

		private static final long serialVersionUID = 5771186427389043634L;
		
		@Description("The indicator to determine whether to retrieve document-topic or topic-term description. "
				+ "Supported values are 'document-topic' and 'topic-term'. Default is 'document-topic'.")
		@Macro
		public String topicStrategy;
		
		public TopicConfig() {
			topicStrategy = "document-topic";
		}
		
	}
}
