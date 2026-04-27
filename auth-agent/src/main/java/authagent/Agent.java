package authagent;

import com.google.adk.a2a.executor.AgentExecutor;
import com.google.adk.a2a.executor.AgentExecutorConfig;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.adk.web.AgentLoader;
import com.google.adk.web.AgentStaticLoader;
import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.EventKind;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth Agent — تایید هویت کاربران (port 8081)
 * TODO: متد verifyUser رو به Auth Service واقعی بانک وصل کن
 */
@Configuration
@ComponentScan(basePackages = "authagent")
public class Agent {

  public static final LlmAgent ROOT_AGENT =
      LlmAgent.builder()
          .name("auth-agent")
          .model("gemini-3-flash-preview")
          .description("Verifies user identity for banking operations.")
          .instruction(
              "You are a bank authentication agent. "
              + "Call verify_user tool with the userId and return the result.")
          .tools(FunctionTool.create(Agent.class, "verifyUser"))
          .build();

  @Bean public BaseAgent rootAgent() { return ROOT_AGENT; }
  @Bean public AgentLoader agentLoader() { return new AgentStaticLoader(ROOT_AGENT); }

  @Bean
  public AgentExecutor a2aAgentExecutor(BaseAgent rootAgent) {
    return new AgentExecutor.Builder()
        .agent(rootAgent)
        .appName(ROOT_AGENT.name())
        .sessionService(new InMemorySessionService())
        .artifactService(new InMemoryArtifactService())
        .agentExecutorConfig(AgentExecutorConfig.builder().build())
        .build();
  }

  @Bean
  public DefaultRequestHandler defaultRequestHandler(AgentExecutor executor) {
    InMemoryTaskStore taskStore = new InMemoryTaskStore();
    InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore);
    InMemoryPushNotificationConfigStore pushConfigStore = new InMemoryPushNotificationConfigStore();
    BasePushNotificationSender pushSender = new BasePushNotificationSender(pushConfigStore);
    return DefaultRequestHandler.create(executor, taskStore, queueManager,
        pushConfigStore, pushSender, Executors.newCachedThreadPool());
  }

  @RestController
  public static class A2AController {
    private final DefaultRequestHandler requestHandler;
    public A2AController(DefaultRequestHandler requestHandler) { this.requestHandler = requestHandler; }

    @PostMapping("/a2a/remote/v1/message:send")
    public ResponseEntity<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest req) {
      ServerCallContext ctx = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());
      EventKind result = requestHandler.onMessageSend(req.getParams(), ctx);
      return ResponseEntity.ok(new SendMessageResponse(req.getId(), result));
    }
  }

  /**
   * TODO (Production): RestTemplate rest = new RestTemplate();
   * return rest.getForObject("https://auth.yourbank.com/verify/" + userId, Map.class);
   */
  public static Map<String, String> verifyUser(
      @Schema(name = "userId", description = "User ID to authenticate") String userId) {
    if (userId == null || userId.isBlank())
      return Map.of("status", "failed", "message", "Invalid userId");
    return Map.of("status", "authenticated", "userId", userId,
        "message", "User " + userId + " is verified and authenticated.");
  }

  @RestController
  public static class AgentCardController {
    @Value("${app.url}") private String appUrl;

    @GetMapping("/.well-known/agent-card.json")
    public AgentCard getAgentCard() {
      List<AgentSkill> skills = ROOT_AGENT.tools().blockingGet().stream()
          .map(t -> new AgentSkill.Builder().id(ROOT_AGENT.name()+"-"+t.name())
              .name(t.name()).description(t.description()).tags(List.of("auth","banking")).build())
          .toList();
      return new AgentCard.Builder().name(ROOT_AGENT.name()).description(ROOT_AGENT.description())
          .url(appUrl + "/a2a/remote/v1/message:send").version("1.0.0")
          .capabilities(new AgentCapabilities.Builder().streaming(false).build())
          .defaultInputModes(List.of("text/plain")).defaultOutputModes(List.of("text/plain"))
          .skills(skills).build();
    }
  }
}
