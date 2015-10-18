/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.stream.utils;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderConfiguration;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.binder.DefaultBinderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;

import java.util.Collections;

/**
 * A simple configuration that creates mock {@link org.springframework.cloud.stream.binder.Binder}s.
 *
 * @author Marius Bogoevici
 */
@Configuration
public class MockBinderConfiguration {

	@Bean
	public BinderFactory<?> binderFactory() {
		return new DefaultBinderFactory<>(
				Collections.singletonMap("mock",new BinderConfiguration("mock",new Class[]{MockBinderConfig.class})));
	}

	@Bean
	public Binder<?> defaultBinder(BinderFactory<MessageChannel> binderFactory) {
		return binderFactory.getBinder(null);
	}

}
