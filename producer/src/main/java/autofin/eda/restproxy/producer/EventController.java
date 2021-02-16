package autofin.eda.restproxy.producer;

import autofin.eda.restproxy.producer.to.EventSubmission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
@CrossOrigin
@RestController
public class EventController {
    @Value("${kafka.topicName}")
    private String topicName;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @ApiOperation(value = "Send an event", notes = "Send an event", tags = {"Events"})
    @PostMapping(value = "/event", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    @ResponseBody
    public ResponseEntity sendEvent(@ApiParam(name = "event", value = "Send a single Event", required = true) @RequestBody(required = true) @Valid EventSubmission submission) throws JsonProcessingException, ExecutionException, InterruptedException {
        Event event = Event.builder()
                .id(UUID.randomUUID().toString())
                .created(LocalDateTime.now())
                .name(submission.getName())
                .description(submission.getDescription())
                .sentBy(StringUtils.defaultString(submission.getSentBy(), "anonymous"))
                .build();
        ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(topicName, objectMapper.writeValueAsString(event));
        SendResult<String, String> result = future.get();
        return new ResponseEntity(Map.of("result", result.toString()), HttpStatus.OK);
    }
}
