/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.schema;

import org.apache.avro.Schema;

/**
 * @author Vinicius Carvalho
 * @author Marius Bogoevici
 */
public interface SchemaRegistryClient {

	/**
	 * Registers a schema with the remote repository returning the unique identifier associated with this schema.
	 * version
	 * @param subject the full name of the schena
	 * @param schema
	 * @return a {@link SchemaRegistrationResponse} representing the result of the operation
	 */
	SchemaRegistrationResponse register(String subject, Schema schema);

	/**
	 * Retrieves a schema by its identifier.
	 * @param id
	 * @return
	 */
	Schema fetch(SchemaReference id);

}