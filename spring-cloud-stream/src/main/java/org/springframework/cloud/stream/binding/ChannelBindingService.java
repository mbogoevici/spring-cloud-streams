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

package org.springframework.cloud.stream.binding;

import java.util.Properties;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderProperties;
import org.springframework.cloud.stream.config.ChannelBindingProperties;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Handles the binding of input/output channels by delegating to an underlying
 * {@link Binder}.
 *
 * @author Mark Fisher
 * @author Dave Syer
 * @author Marius Bogoevici
 */
public class ChannelBindingService {

	private Binder<MessageChannel> binder;

	private ChannelBindingProperties channelBindingProperties;

	public ChannelBindingService(ChannelBindingProperties channelBindingProperties,
			Binder<MessageChannel> binder) {
		this.channelBindingProperties = channelBindingProperties;
		this.binder = binder;
	}

	public void bindConsumer(MessageChannel inputChannel, String inputChannelName) {
		String channelBindingTarget = this.channelBindingProperties
				.getBindingTarget(inputChannelName);
		if (isChannelPubSub(channelBindingTarget)) {
			this.binder.bindPubSubConsumer(removePrefix(channelBindingTarget),
					inputChannel, this.channelBindingProperties.getConsumerProperties());
		}
		else {
			this.binder.bindConsumer(channelBindingTarget, inputChannel,
					this.channelBindingProperties.getConsumerProperties());
		}
	}

	public void bindProducer(MessageChannel outputChannel, String outputChannelName) {
		String channelBindingTarget = this.channelBindingProperties
				.getBindingTarget(outputChannelName);
		if (isChannelPubSub(channelBindingTarget)) {
			this.binder.bindPubSubProducer(removePrefix(channelBindingTarget),
					outputChannel, this.channelBindingProperties.getProducerProperties());
		}
		else {
			this.binder.bindProducer(channelBindingTarget, outputChannel,
					this.channelBindingProperties.getProducerProperties());
		}
	}

	private boolean isChannelPubSub(String bindingTarget) {
		Assert.isTrue(StringUtils.hasText(bindingTarget),
				"Binding target should not be empty/null.");
		return bindingTarget.startsWith("topic:");
	}

	private String removePrefix(String bindingTarget) {
		Assert.isTrue(StringUtils.hasText(bindingTarget), "Binding target should not be empty/null.");
		return bindingTarget.substring(bindingTarget.indexOf(":") + 1);
	}

	public void unbindConsumers(String inputChannelName) {
		this.binder.unbindConsumers(inputChannelName);
	}

	public void unbindProducers(String outputChannelName) {
		this.binder.unbindProducers(outputChannelName);
	}

	public Properties getBindingConsumerProperties(String inputChannelName) {
		if (channelBindingProperties.isPartitioned(inputChannelName)) {
			Properties bindingConsumerProperties = new Properties();
			if (channelBindingProperties.getConsumerProperties() == null) {
				bindingConsumerProperties.putAll(channelBindingProperties.getConsumerProperties());
			}
			bindingConsumerProperties.put(BinderProperties.COUNT, channelBindingProperties.getInstanceCount());
			bindingConsumerProperties.put(BinderProperties.PARTITION_INDEX,
					channelBindingProperties.getInstanceIndex());
			bindingConsumerProperties.put(BinderProperties.MIN_PARTITION_COUNT,
					channelBindingProperties.getPartitionCount());
			bindingConsumerProperties.put(BinderProperties.NEXT_MODULE_COUNT,
					channelBindingProperties.getPartitionCount());
		}
		else {
			return channelBindingProperties.getConsumerProperties();
		}
	}
}
