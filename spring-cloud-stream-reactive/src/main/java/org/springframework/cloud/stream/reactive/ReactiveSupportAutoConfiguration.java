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

package org.springframework.cloud.stream.reactive;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Marius Bogoevici
 */
@Configuration
public class ReactiveSupportAutoConfiguration {

	@Bean
	public MessageChannelToInputFluxArgumentAdapter messageChannelToInputFluxArgumentAdapter(
			CompositeMessageConverterFactory compositeMessageConverterFactory) {
		return new MessageChannelToInputFluxArgumentAdapter(
				compositeMessageConverterFactory.getMessageConverterForAllRegistered());
	}

	@Bean
	public MessageChannelToFluxSenderArgumentAdapter messageChannelToFluxSenderArgumentAdapter() {
		return new MessageChannelToFluxSenderArgumentAdapter();
	}

	@Bean
	FluxToMessageChannelResultAdapter fluxToMessageChannelResultAdapter() {
		return new FluxToMessageChannelResultAdapter();
	}

	@Configuration
	@ConditionalOnClass(name = "rx.Observable")
	public static class RxJava1SupportConfiguration {

		@Bean
		public MessageChannelToInputObservableArgumentAdapter messageChannelToInputObservableArgumentAdapter(
				MessageChannelToInputFluxArgumentAdapter messageChannelToFluxArgumentAdapter) {
			return new MessageChannelToInputObservableArgumentAdapter(messageChannelToFluxArgumentAdapter);
		}

		@Bean
		public MessageChannelToObservableSenderArgumentAdapter messageChannelToObservableSenderArgumentAdapter(
				MessageChannelToFluxSenderArgumentAdapter messageChannelToFluxSenderArgumentAdapter) {
			return new MessageChannelToObservableSenderArgumentAdapter(messageChannelToFluxSenderArgumentAdapter);
		}

		@Bean
		ObservableToMessageChannelResultAdapter
		observableToMessageChannelResultAdapter(
				FluxToMessageChannelResultAdapter fluxToMessageChannelResultAdapter) {
			return new ObservableToMessageChannelResultAdapter(fluxToMessageChannelResultAdapter);
		}
	}
}