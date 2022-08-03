package autofin.eda.restproxy.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@EnableScheduling
public class EventListener {
    @Value("${kafka.rest.proxy}")
    private String kafkaRestProxyUrl;
    @Value("${listener.consumer.id}")
    private String consumerId;
    @Value("${listener.consumer.group.id}")
    private String consumerGroupId;
    @Value("${listener.auto.offset.reset}")
    private String autoOffsetReset;

    @Value("${topic.name}")
    private String topicName;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    final private MediaType kafkaV2Json = new MediaType("application", "vnd.kafka.v2+json");
    final private MediaType kafkaJsonV2Json = new MediaType("application", "vnd.kafka.json.v2+json");

    private String consumerUrl() {
        return kafkaRestProxyUrl + "/consumers/" + consumerGroupId;
    }

    private String consumerInstanceUrl() {
        return consumerUrl() + "/instances/" + consumerId;
    }

    /**
     * Delete the kafka consumer.
     */
    private void deleteConsumer() {
        logger.info("Deleting consumer instance "+ consumerId);
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.ALL));
        headers.setContentType(kafkaV2Json);
        HttpEntity request = new HttpEntity(headers);
        try {
            ResponseEntity<Void> deleteConsumerRes = restTemplate.exchange(consumerInstanceUrl(), HttpMethod.DELETE, request, Void.class);
        } catch (RestClientException e) {
            logger.warn(e.toString());
        }
    }

    /**
     * Create the consumer instance (in the server).
     */
    private void createConsumer() {
        logger.info("Creating consumer instance "+ consumerId);
        final Map<String, String> createConsumerParams = Map.<String, String>of(
                "name", consumerId,
                "format", "json",
                "auto.offset.reset", autoOffsetReset);

        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.ALL));
        headers.setContentType(kafkaV2Json);

        // Create consumer
        HttpEntity<Map<String, String>> createConsumerReq = new HttpEntity<Map<String, String>>(createConsumerParams, headers);
        ResponseEntity<Map<String, String>> createConsumerRes = restTemplate.exchange(consumerUrl(), HttpMethod.POST, createConsumerReq, new ParameterizedTypeReference<Map<String, String>>() {
        });
        logger.info(createConsumerRes.getBody().toString());

        final String baseUri = createConsumerRes.getBody().get("base_uri");
        assert baseUri != null && !baseUri.isEmpty();
        /**
         * For simplicity, we discard the response above; but for realworld applications, the baseUri returned, above,
         * is what you would use for further communications with the KRP.
         * (we just happen to know how to construct the effective URLs in this example.
         */
    }

    /**
     * Subscribe to one or more topics.
     */
    private void subscribe() {
        logger.info("Subscribing to " + topicName);
        final Map<String, Object> subscribeParams = Map.<String, Object>of(
                "topics", StringUtils.split(topicName,",")
        );
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.ALL));
        headers.setContentType(kafkaV2Json);
        final HttpEntity request = new HttpEntity(subscribeParams,headers);
        ResponseEntity<Void> response = restTemplate.exchange(consumerInstanceUrl() + "/subscription", HttpMethod.POST, request, Void.class);
        assert response.getStatusCode().equals(HttpStatus.NO_CONTENT) || response.getStatusCode().equals(HttpStatus.OK);
    }


    @PostConstruct
    private void initialize() {
        deleteConsumer();
        createConsumer();
        subscribe();
    }

    @PreDestroy
    private void cleanup() {
        deleteConsumer();
    }

    /**
     * Listen on the topic.
     * @throws JsonProcessingException
     */
    @Scheduled(fixedDelayString = "${listener.consumer.idle-between-poll.ms}")
    public void listener() throws JsonProcessingException {
        if((((System.currentTimeMillis() / 1000L) / 60L) % 5) == 0L) {
            logger.info("Listening for events from " + topicName);
        }
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(kafkaJsonV2Json));
        HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(headers);
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(consumerInstanceUrl() + "/records", HttpMethod.GET, request, new ParameterizedTypeReference<List<Map<String, Object>>>() {
        });
        List<Map<String, Object>> events = response.getBody();
        if (events.size() == 0) {
            // We're doing this because of: https://github.com/confluentinc/kafka-rest/issues/432
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
            }
            response = restTemplate.exchange(consumerInstanceUrl() + "/records", HttpMethod.GET, request, new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
            events = response.getBody();
        }
        // ..now dispatch the events to the actual handler.
        events.forEach(e -> {
            // consume(objectMapper.convertValue(e.get("value"), Event.class));
            try {
                logger.info(e.get("topic") + "\n" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(e.get("value")));
            } catch (JsonProcessingException ex) {
                logger.error("Problems reading " + e.get("value").toString(), ex);
            }
        });
    }

    /**
     * This is the main driver.
     * @param event
     */
    public void consume(Event event) {
        logger.info("Got event: " + event.toString());
    }
}
