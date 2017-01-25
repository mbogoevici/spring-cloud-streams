/*
 * Copyright 2017 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;

/**
 * An {@link AbstractReplyProducingMessageHandler} that delegates
 * @author Marius Bogoevici
 */
final class DispatchingStreamListenerMessageHandler extends AbstractReplyProducingMessageHandler {

	private final Collection<ConditionalStreamListenerHandler> handlerMethods;

	private final EvaluationContext evaluationContext;

	DispatchingStreamListenerMessageHandler(Collection<ConditionalStreamListenerHandler> handlerMethods,
			EvaluationContext evaluationContext) {
		Assert.notEmpty(handlerMethods, "'handlerMethods' cannot be empty");
		Assert.notNull(evaluationContext, "'evaluationContext' cannot be empty");
		this.handlerMethods = handlerMethods;
		this.evaluationContext = evaluationContext;
	}

	@Override
	protected boolean shouldCopyRequestHeaders() {
		return false;
	}

	@Override
	protected Object handleRequestMessage(Message<?> requestMessage) {
		Collection<ConditionalStreamListenerHandler> matchingHandlers = findMatchingHandlers(requestMessage);
		if (matchingHandlers.size() == 0) {
			if (logger.isWarnEnabled()) {
				logger.warn("Cannot find a @StreamListener matching " + requestMessage);
			}
			return null;
		}
		else if (matchingHandlers.size() > 1) {
			for (ConditionalStreamListenerHandler matchingHandler : matchingHandlers) {
				if (!matchingHandler.isVoid()) {
					throw new MessagingException(
							"Multiple matching methods cannot return values for " + requestMessage);
				}
			}
			for (ConditionalStreamListenerHandler matchingMethod : matchingHandlers) {
				matchingMethod.handleMessage(requestMessage);
			}
			return null;
		}
		else {
			final ConditionalStreamListenerHandler singleMatchingHandler = matchingHandlers.iterator().next();
			singleMatchingHandler.handleMessage(requestMessage);
			return null;
		}
	}

	private Collection<ConditionalStreamListenerHandler> findMatchingHandlers(Message<?> message) {
		ArrayList<ConditionalStreamListenerHandler> matchingMethods = new ArrayList<>();
		for (ConditionalStreamListenerHandler conditionalStreamListenerHandlerMethod : this.handlerMethods) {
			if (conditionalStreamListenerHandlerMethod.getCondition() == null) {
				matchingMethods.add(conditionalStreamListenerHandlerMethod);
			}
			else {
				Object value = conditionalStreamListenerHandlerMethod.getCondition().getValue(this.evaluationContext, message);
				if (Boolean.class.isAssignableFrom(value.getClass())) {
					if ((Boolean) value) {
						matchingMethods.add(conditionalStreamListenerHandlerMethod);
					}
				}
				else if (Boolean.valueOf(String.valueOf(value))) {
					matchingMethods.add(conditionalStreamListenerHandlerMethod);
				}
			}
		}
		return matchingMethods;
	}

	static class ConditionalStreamListenerHandler implements MessageHandler {

		private Expression condition;

		private StreamListenerMessageHandler streamListenerMessageHandler;

		ConditionalStreamListenerHandler(Expression condition, StreamListenerMessageHandler streamListenerMessageHandler) {
			this.condition = condition;
			this.streamListenerMessageHandler = streamListenerMessageHandler;
		}

		public Expression getCondition() {
			return condition;
		}

		public boolean isVoid() {
			return this.streamListenerMessageHandler.isVoid();
		}

		@Override
		public void handleMessage(Message<?> message) throws MessagingException {
			this.streamListenerMessageHandler.handleMessage(message);
		}
	}
}
