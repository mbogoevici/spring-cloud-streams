/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.cloud.stream.binder.kafka;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import kafka.api.OffsetRequest;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.PartitionCapableBinderTests;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.cloud.stream.binder.Spy;
import org.springframework.cloud.stream.binder.kafka.config.KafkaBinderProperties;
import org.springframework.cloud.stream.test.junit.kafka.KafkaTestSupport;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.kafka.core.KafkaMessage;
import org.springframework.integration.kafka.core.Partition;
import org.springframework.integration.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.integration.kafka.listener.MessageListener;
import org.springframework.integration.kafka.support.ProducerConfiguration;
import org.springframework.integration.kafka.support.ProducerMetadata;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;


/**
 * Integration tests for the {@link KafkaMessageChannelBinder}.
 * @author Eric Bottard
 * @author Marius Bogoevici
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 */
public class KafkaBinderTests extends PartitionCapableBinderTests {

	private final String CLASS_UNDER_TEST_NAME = KafkaMessageChannelBinder.class.getSimpleName();

	@ClassRule
	public static KafkaTestSupport kafkaTestSupport = new KafkaTestSupport(true);

	private KafkaTestBinder binder;

	@Override
	protected void binderBindUnbindLatency() throws InterruptedException {
		Thread.sleep(500);
	}

	@Override
	protected KafkaTestBinder getBinder() {
		return getBinder(new KafkaBinderProperties());
	}

	protected KafkaTestBinder getBinder(KafkaBinderProperties kafkaBinderProperties) {
		if (binder == null) {
			binder = new KafkaTestBinder(kafkaTestSupport, kafkaBinderProperties);
		}
		return binder;
	}

	@Override
	protected ConsumerProperties createConsumerProperties() {
		return new ConsumerProperties();
	}

	@Override
	protected ProducerProperties createProducerProperties() {
		return new ProducerProperties();
	}

	@Before
	public void init() {
		String multiplier = System.getenv("KAFKA_TIMEOUT_MULTIPLIER");
		if (multiplier != null) {
			timeoutMultiplier = Double.parseDouble(multiplier);
		}
	}

	@Override
	protected boolean usesExplicitRouting() {
		return false;
	}

	@Override
	protected String getClassUnderTestName() {
		return CLASS_UNDER_TEST_NAME;
	}

	@Override
	public Spy spyOn(final String name) {
		KafkaMessageChannelBinder.validateTopicName(name);

		KafkaTestBinder binderWrapper = getBinder();
		// Rewind offset, as tests will have typically already sent the messages we're trying to consume

		KafkaMessageListenerContainer messageListenerContainer = binderWrapper.getCoreBinder().createMessageListenerContainer(
				createConsumerProperties(), UUID.randomUUID().toString(), name, null, OffsetRequest.EarliestTime());

		final BlockingQueue<KafkaMessage> messages = new ArrayBlockingQueue<KafkaMessage>(10);

		messageListenerContainer.setMessageListener(new MessageListener() {

			@Override
			public void onMessage(KafkaMessage message) {
				messages.offer(message);
			}
		});


		return new Spy() {

			@Override
			public Object receive(boolean expectNull) throws Exception {
				return messages.poll(expectNull ? 50 : 5000, TimeUnit.MILLISECONDS);
			}
		};

	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidateKafkaTopicName() {
		KafkaMessageChannelBinder.validateTopicName("foo:bar");
	}

	@Test
	public void testCompression() throws Exception {
		final ProducerMetadata.CompressionType[] codecs = new ProducerMetadata.CompressionType[] {
				ProducerMetadata.CompressionType.none,
				ProducerMetadata.CompressionType.gzip,
				ProducerMetadata.CompressionType.snappy };

		byte[] ratherBigPayload = new byte[2048];
		Arrays.fill(ratherBigPayload, (byte) 65);
		KafkaBinderProperties kafkaBinderProperties = new KafkaBinderProperties();
		KafkaTestBinder binder = getBinder(kafkaBinderProperties);

		for (ProducerMetadata.CompressionType codec : codecs) {
			DirectChannel moduleOutputChannel = new DirectChannel();
			QueueChannel moduleInputChannel = new QueueChannel();
			ProducerProperties producerProperties = new ProducerProperties();
			kafkaBinderProperties.setCompressionType(codec);
			Binding<MessageChannel> producerBinding = binder.bindProducer("foo.0", moduleOutputChannel, producerProperties);
			Binding<MessageChannel> consumerBinding = binder.bindConsumer("foo.0", "test", moduleInputChannel, new ConsumerProperties());
			Message<?> message = org.springframework.integration.support.MessageBuilder.withPayload(ratherBigPayload).build();
			// Let the consumer actually bind to the producer before sending a msg
			binderBindUnbindLatency();
			moduleOutputChannel.send(message);
			Message<?> inbound = receive(moduleInputChannel);
			assertNotNull(inbound);
			assertArrayEquals(ratherBigPayload, (byte[]) inbound.getPayload());
			producerBinding.unbind();
			consumerBinding.unbind();
		}
	}

	@Test
	public void testCustomPartitionCountOverridesDefaultIfLarger() throws Exception {

		byte[] ratherBigPayload = new byte[2048];
		Arrays.fill(ratherBigPayload, (byte) 65);
		KafkaBinderProperties kafkaBinderProperties = new KafkaBinderProperties();
		kafkaBinderProperties.setMinPartitionCount(10);
		KafkaTestBinder binder = getBinder(kafkaBinderProperties);

		DirectChannel moduleOutputChannel = new DirectChannel();
		QueueChannel moduleInputChannel = new QueueChannel();
		ProducerProperties producerProperties = new ProducerProperties();
		producerProperties.setPartitionCount(10);
		ConsumerProperties consumerProperties = new ConsumerProperties();
		long uniqueBindingId = System.currentTimeMillis();
		Binding<MessageChannel> producerBinding = binder.bindProducer("foo" + uniqueBindingId + ".0", moduleOutputChannel, producerProperties);
		Binding<MessageChannel> consumerBinding = binder.bindConsumer("foo" + uniqueBindingId + ".0", null, moduleInputChannel, consumerProperties);
		Message<?> message = org.springframework.integration.support.MessageBuilder.withPayload(ratherBigPayload).build();
		// Let the consumer actually bind to the producer before sending a msg
		binderBindUnbindLatency();
		moduleOutputChannel.send(message);
		Message<?> inbound = receive(moduleInputChannel);
		assertNotNull(inbound);
		assertArrayEquals(ratherBigPayload, (byte[]) inbound.getPayload());
		Collection<Partition> partitions = binder.getCoreBinder().getConnectionFactory().getPartitions(
				"foo" + uniqueBindingId + ".0");
		assertThat(partitions, hasSize(10));
		producerBinding.unbind();
		consumerBinding.unbind();
	}

	@Test
	public void testCustomPartitionCountDoesNotOverridePartitioningIfSmaller() throws Exception {

		byte[] ratherBigPayload = new byte[2048];
		Arrays.fill(ratherBigPayload, (byte) 65);
		KafkaBinderProperties kafkaBinderProperties = new KafkaBinderProperties();
		kafkaBinderProperties.setMinPartitionCount(3);
		KafkaTestBinder binder = getBinder(kafkaBinderProperties);

		DirectChannel moduleOutputChannel = new DirectChannel();
		QueueChannel moduleInputChannel = new QueueChannel();
		ProducerProperties producerProperties = new ProducerProperties();
		producerProperties.setPartitionCount(5);
		producerProperties.setPartitionKeyExpression(spelExpressionParser.parseExpression("payload"));
		ConsumerProperties consumerProperties = new ConsumerProperties();
		long uniqueBindingId = System.currentTimeMillis();
		Binding<MessageChannel> producerBinding = binder.bindProducer("foo" + uniqueBindingId + ".0", moduleOutputChannel, producerProperties);
		Binding<MessageChannel> consumerBinding = binder.bindConsumer("foo" + uniqueBindingId + ".0", null, moduleInputChannel, consumerProperties);
		Message<?> message = org.springframework.integration.support.MessageBuilder.withPayload(ratherBigPayload).build();
		// Let the consumer actually bind to the producer before sending a msg
		binderBindUnbindLatency();
		moduleOutputChannel.send(message);
		Message<?> inbound = receive(moduleInputChannel);
		assertNotNull(inbound);
		assertArrayEquals(ratherBigPayload, (byte[]) inbound.getPayload());
		Collection<Partition> partitions = binder.getCoreBinder().getConnectionFactory().getPartitions(
				"foo" + uniqueBindingId + ".0");
		assertThat(partitions, hasSize(5));
		producerBinding.unbind();
		consumerBinding.unbind();
	}

	@Test
	public void testCustomPartitionCountOverridesPartitioningIfLarger() throws Exception {

		byte[] ratherBigPayload = new byte[2048];
		Arrays.fill(ratherBigPayload, (byte) 65);
		KafkaTestBinder binder = getBinder();

		DirectChannel moduleOutputChannel = new DirectChannel();
		QueueChannel moduleInputChannel = new QueueChannel();
		ProducerProperties producerProperties = new ProducerProperties();
		producerProperties.setPartitionCount(5);
		producerProperties.setPartitionKeyExpression(spelExpressionParser.parseExpression("payload"));
		ConsumerProperties consumerProperties = new ConsumerProperties();
		long uniqueBindingId = System.currentTimeMillis();
		Binding<MessageChannel> producerBinding = binder.bindProducer("foo" + uniqueBindingId + ".0", moduleOutputChannel, producerProperties);
		Binding<MessageChannel> consumerBinding = binder.bindConsumer("foo" + uniqueBindingId + ".0", null, moduleInputChannel, consumerProperties);
		Message<?> message = org.springframework.integration.support.MessageBuilder.withPayload(ratherBigPayload).build();
		// Let the consumer actually bind to the producer before sending a msg
		binderBindUnbindLatency();
		moduleOutputChannel.send(message);
		Message<?> inbound = receive(moduleInputChannel);
		assertNotNull(inbound);
		assertArrayEquals(ratherBigPayload, (byte[]) inbound.getPayload());
		Collection<Partition> partitions = binder.getCoreBinder().getConnectionFactory().getPartitions(
				"foo" + uniqueBindingId + ".0");
		assertThat(partitions, hasSize(5));
		producerBinding.unbind();
		consumerBinding.unbind();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testDefaultConsumerStartsAtEarliest() throws Exception {
		KafkaBinderProperties kafkaBinderProperties = new KafkaBinderProperties();
		KafkaTestBinder binder = getBinder(kafkaBinderProperties);
		DirectChannel output = new DirectChannel();
		QueueChannel input1 = new QueueChannel();

		String testTopicName = UUID.randomUUID().toString();
		binder.bindProducer(testTopicName, output, new ProducerProperties());
		String testPayload1 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload1.getBytes()));
		binder.bindConsumer(testTopicName, "startOffsets", input1, new ConsumerProperties());
		Message<byte[]> receivedMessage1 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage1, not(nullValue()));
		assertThat(new String(receivedMessage1.getPayload()), equalTo(testPayload1));
		String testPayload2 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload2.getBytes()));
		Message<byte[]> receivedMessage2 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage2, not(nullValue()));
		assertThat(new String(receivedMessage2.getPayload()), equalTo(testPayload2));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testEarliest() throws Exception {
		KafkaBinderProperties kafkaBinderProperties = new KafkaBinderProperties();
		kafkaBinderProperties.setStartOffset(KafkaMessageChannelBinder.StartOffset.earliest);
		KafkaTestBinder binder = getBinder(kafkaBinderProperties);
		DirectChannel output = new DirectChannel();
		QueueChannel input1 = new QueueChannel();

		String testTopicName = UUID.randomUUID().toString();
		binder.bindProducer(testTopicName, output, new ProducerProperties());
		String testPayload1 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload1.getBytes()));
		ConsumerProperties properties = new ConsumerProperties();
		binder.bindConsumer(testTopicName, "startOffsets", input1, properties);
		Message<byte[]> receivedMessage1 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage1, not(nullValue()));
		String testPayload2 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload2.getBytes()));
		Message<byte[]> receivedMessage2 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage2, not(nullValue()));
		assertThat(new String(receivedMessage2.getPayload()), equalTo(testPayload2));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReset() throws Exception {
		KafkaBinderProperties kafkaBinderProperties = new KafkaBinderProperties();
		kafkaBinderProperties.setResetOffsets(true);
		kafkaBinderProperties.setStartOffset(KafkaMessageChannelBinder.StartOffset.earliest);
		KafkaTestBinder binder = getBinder(kafkaBinderProperties);
		DirectChannel output = new DirectChannel();
		QueueChannel input1 = new QueueChannel();

		String testTopicName = UUID.randomUUID().toString();

		Binding<MessageChannel> producerBinding = binder.bindProducer(testTopicName, output, new ProducerProperties());
		String testPayload1 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload1.getBytes()));
		ConsumerProperties properties = new ConsumerProperties();
		Binding<MessageChannel> consumerBinding =
				binder.bindConsumer(testTopicName, "startOffsets", input1, properties);
		Message<byte[]> receivedMessage1 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage1, not(nullValue()));
		String testPayload2 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload2.getBytes()));
		Message<byte[]> receivedMessage2 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage2, not(nullValue()));
		assertThat(new String(receivedMessage2.getPayload()), equalTo(testPayload2));
		consumerBinding.unbind();

		String testPayload3 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload3.getBytes()));

		ConsumerProperties properties2 = new ConsumerProperties();
		consumerBinding =
				binder.bindConsumer(testTopicName, "startOffsets", input1, properties2);
		Message<byte[]> receivedMessage4 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage4, not(nullValue()));
		assertThat(new String(receivedMessage4.getPayload()), equalTo(testPayload1));
		Message<byte[]> receivedMessage5 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage5, not(nullValue()));
		assertThat(new String(receivedMessage5.getPayload()), equalTo(testPayload2));
		Message<byte[]> receivedMessage6 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage6, not(nullValue()));
		assertThat(new String(receivedMessage6.getPayload()), equalTo(testPayload3));
		consumerBinding.unbind();
		producerBinding.unbind();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testResume() throws Exception {
		KafkaTestBinder binder = getBinder();
		DirectChannel output = new DirectChannel();
		QueueChannel input1 = new QueueChannel();

		String testTopicName = UUID.randomUUID().toString();
		Binding<MessageChannel> producerBinding = binder.bindProducer(testTopicName, output, new ProducerProperties());
		String testPayload1 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload1.getBytes()));
		ConsumerProperties firstConsumerProperties = new ConsumerProperties();
		Binding<MessageChannel> consumerBinding = binder.bindConsumer(testTopicName, "startOffsets", input1, firstConsumerProperties);
		Message<byte[]> receivedMessage1 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage1, not(nullValue()));
		String testPayload2 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload2.getBytes()));
		Message<byte[]> receivedMessage2 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage2, not(nullValue()));
		assertThat(new String(receivedMessage2.getPayload()), equalTo(testPayload2));
		consumerBinding.unbind();

		String testPayload3 = "foo-" + UUID.randomUUID().toString();
		output.send(new GenericMessage<>(testPayload3.getBytes()));

		consumerBinding =
				binder.bindConsumer(testTopicName, "startOffsets", input1, new ConsumerProperties());
		Message<byte[]> receivedMessage3 = (Message<byte[]>) receive(input1);
		assertThat(receivedMessage3, not(nullValue()));
		assertThat(new String(receivedMessage3.getPayload()), equalTo(testPayload3));
		consumerBinding.unbind();
		producerBinding.unbind();
	}

	@Test
	public void testSyncProducerMetadata() throws Exception {
		KafkaBinderProperties kafkaBinderProperties = new KafkaBinderProperties();
		kafkaBinderProperties.setSyncProducer(true);
		KafkaTestBinder binder = getBinder(kafkaBinderProperties);

		DirectChannel output = new DirectChannel();

		String testTopicName = UUID.randomUUID().toString();
		ProducerProperties properties = new ProducerProperties();
		Binding<MessageChannel> producerBinding = binder.bindProducer(testTopicName, output, properties);
		DirectFieldAccessor accessor = new DirectFieldAccessor(extractEndpoint(producerBinding));
		MessageHandler handler = (MessageHandler) accessor.getPropertyValue("handler");
		DirectFieldAccessor accessor1 = new DirectFieldAccessor(handler);
		ProducerConfiguration producerConfiguration = (ProducerConfiguration) accessor1.getPropertyValue("producerConfiguration");
		assertTrue("Kafka Sync Producer should have been enabled.", producerConfiguration.getProducerMetadata().isSync());
		producerBinding.unbind();
	}
}
