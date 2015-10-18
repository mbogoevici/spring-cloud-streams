/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.binder;

/**
 * References one or more {@link org.springframework.context.annotation.Configuration}-annotated classes that
 * provide a context definition which contains exactly one {@link Binder}.
 *
 * @author Marius Bogoevici
 */
public class BinderConfiguration {

	private String name;

	private Class<?> configuration[];

	/**
	 *
	 * @param name a name for configuration (unique among a set of multiple instances)
	 * @param configuration the binder configuration
	 */
	public BinderConfiguration(String name, Class<?>[] configuration) {
		this.name = name;
		this.configuration = configuration;
	}

	public String getName() {
		return name;
	}

	public Class<?>[] getConfiguration() {
		return configuration;
	}
}
