package com.sanad.platform.crm.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableCaching
@EnableRabbit
@EnableConfigurationProperties(CrmPlatformProperties.class)
public class CrmPlatformConfiguration {

    public static final String EVENT_EXCHANGE = "snad.crm.events";
    public static final String DEAD_LETTER_EXCHANGE = "snad.crm.dlx";
    public static final String DEAD_LETTER_QUEUE = "snad.crm.dead-letter";
    public static final String SEARCH_QUEUE = "snad.crm.search";
    public static final String NOTIFICATION_QUEUE = "snad.crm.notifications";
    public static final String WEBHOOK_QUEUE = "snad.crm.webhooks";
    public static final String IMPORT_QUEUE = "snad.crm.imports";
    public static final String WORKFLOW_QUEUE = "snad.crm.workflows";

    @Bean
    HttpClient crmPlatformHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_2)
            .build();
    }

    @Bean
    Jackson2JsonMessageConverter crmJsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    TopicExchange crmEventExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange crmDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue crmDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding crmDeadLetterBinding(Queue crmDeadLetterQueue, DirectExchange crmDeadLetterExchange) {
        return BindingBuilder.bind(crmDeadLetterQueue).to(crmDeadLetterExchange).with("dead");
    }

    @Bean
    Queue crmSearchQueue() { return durableQueue(SEARCH_QUEUE); }

    @Bean
    Queue crmNotificationQueue() { return durableQueue(NOTIFICATION_QUEUE); }

    @Bean
    Queue crmWebhookQueue() { return durableQueue(WEBHOOK_QUEUE); }

    @Bean
    Queue crmImportQueue() { return durableQueue(IMPORT_QUEUE); }

    @Bean
    Queue crmWorkflowQueue() { return durableQueue(WORKFLOW_QUEUE); }

    @Bean
    Binding crmSearchBinding(Queue crmSearchQueue, TopicExchange crmEventExchange) {
        return BindingBuilder.bind(crmSearchQueue).to(crmEventExchange).with("crm.search.#");
    }

    @Bean
    Binding crmNotificationBinding(Queue crmNotificationQueue, TopicExchange crmEventExchange) {
        return BindingBuilder.bind(crmNotificationQueue).to(crmEventExchange).with("crm.notification.#");
    }

    @Bean
    Binding crmWebhookBinding(Queue crmWebhookQueue, TopicExchange crmEventExchange) {
        return BindingBuilder.bind(crmWebhookQueue).to(crmEventExchange).with("crm.webhook.#");
    }

    @Bean
    Binding crmImportBinding(Queue crmImportQueue, TopicExchange crmEventExchange) {
        return BindingBuilder.bind(crmImportQueue).to(crmEventExchange).with("crm.import.#");
    }

    @Bean
    Binding crmWorkflowBinding(Queue crmWorkflowQueue, TopicExchange crmEventExchange) {
        return BindingBuilder.bind(crmWorkflowQueue).to(crmEventExchange).with("crm.workflow.#");
    }

    @Bean
    CommandLineRunner configureCrmRabbitTemplate(
            RabbitTemplate rabbitTemplate,
            Jackson2JsonMessageConverter converter) {
        return args -> rabbitTemplate.setMessageConverter(converter);
    }

    private Queue durableQueue(String name) {
        return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "dead")
            .build();
    }
}
