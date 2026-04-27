package memoryagent;

import com.google.adk.a2a.executor.AgentExecutor;
import com.google.adk.a2a.executor.AgentExecutorConfig;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Memory Agent — port 8086
 *
 * Provides 3 tools to the bank orchestrator:
 *   1. load_memory   — load user profile + populate session state
 *   2. save_memory   — upsert profile + save embedding + session summary
 *   3. search_memory — semantic similarity search over past conversations
 *
 * All DB operations are wrapped in graceful fallback; the agent works without PostgreSQL.
 */
@Configuration
@ComponentScan(basePackages = "memoryagent")
@EnableJpaRepositories(basePackages = "memoryagent.repository")
public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    /**
     * Static holder — allows static tool methods to call Spring-managed MemoryService.
     * Set via @Autowired setter below, which runs after Spring context initializes.
     */
    private static volatile BankMemoryService memoryService;

    @Autowired
    public void setMemoryService(BankMemoryService ms) {
        Agent.memoryService = ms;
        log.info("[Memory] MemoryService injected into Agent static holder");
    }

    // ── Tool 1: load_memory ───────────────────────────────────────────────────

    public static Map<String, Object> loadMemory(
            @Schema(name = "userId", description = "User ID to load memory for") String userId,
            @Schema(name = "toolContext") ToolContext toolContext) {

        if (memoryService == null) {
            log.warn("[Memory] loadMemory called before MemoryService was ready");
            return Map.of("status", "unavailable", "message", "Memory service not ready");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) toolContext.state();
        return memoryService.loadMemory(userId, state != null ? state : Map.of());
    }

    // ── Tool 2: save_memory ───────────────────────────────────────────────────

    public static Map<String, Object> saveMemory(
            @Schema(name = "userId",        description = "User ID")           String userId,
            @Schema(name = "fromAccount",   description = "Source account")    String fromAccount,
            @Schema(name = "toAccount",     description = "Destination account") String toAccount,
            @Schema(name = "transactionId", description = "Transaction ID")    String transactionId,
            @Schema(name = "sessionId",     description = "Session ID")        String sessionId,
            @Schema(name = "toolContext")   ToolContext toolContext) {

        if (memoryService == null) {
            return Map.of("status", "unavailable", "message", "Memory service not ready");
        }
        return memoryService.saveMemory(userId, fromAccount, toAccount, transactionId, sessionId);
    }

    // ── Tool 3: search_memory ─────────────────────────────────────────────────

    public static Map<String, Object> searchMemory(
            @Schema(name = "userId", description = "User ID to search memories for") String userId,
            @Schema(name = "query",  description = "Natural language search query")  String query,
            @Schema(name = "toolContext") ToolContext toolContext) {

        if (memoryService == null) {
            return Map.of("status", "unavailable", "message", "Memory service not ready",
                    "results", List.of());
        }
        return memoryService.searchMemory(userId, query);
    }

    // ── ADK Agent definition ──────────────────────────────────────────────────

    public static final LlmAgent ROOT_AGENT = LlmAgent.builder()
            .name("memory-agent")
            .model("gemini-2.5-flash")
            .description("Provides long-term memory with RAG for banking users.")
            .instruction("""
                You are a silent memory sub-agent. You are NEVER talking to a human directly.
                You are called by an orchestrator agent with a specific instruction.

                RULES — no exceptions:
                - Do NOT greet. Do NOT introduce yourself. Do NOT ask what the user wants.
                - Do NOT produce any text before or after the tool result.
                - Read the incoming message, identify which tool to call, call it immediately, return its result. Done.

                Tools:
                1. load_memory(userId)       — retrieve past preferences and transaction history.
                2. save_memory(userId, fromAccount, toAccount, transactionId, sessionId) — persist a transaction.
                3. search_memory(userId, query) — semantic search over past conversations.
                """)
            .tools(
                FunctionTool.create(Agent.class, "loadMemory"),
                FunctionTool.create(Agent.class, "saveMemory"),
                FunctionTool.create(Agent.class, "searchMemory"))
            .build();

    @Bean public BaseAgent rootAgent()   { return ROOT_AGENT; }
    @Bean public AgentLoader agentLoader() { return new AgentStaticLoader(ROOT_AGENT); }

    // ── A2A infrastructure ────────────────────────────────────────────────────

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
        InMemoryTaskStore taskStore                     = new InMemoryTaskStore();
        InMemoryQueueManager queueManager              = new InMemoryQueueManager(taskStore);
        InMemoryPushNotificationConfigStore pushConfig = new InMemoryPushNotificationConfigStore();
        BasePushNotificationSender pushSender          = new BasePushNotificationSender(pushConfig);
        return DefaultRequestHandler.create(executor, taskStore, queueManager,
                pushConfig, pushSender, Executors.newCachedThreadPool());
    }

    @RestController
    public static class A2AController {
        private final DefaultRequestHandler requestHandler;
        public A2AController(DefaultRequestHandler rh) { this.requestHandler = rh; }

        @PostMapping("/a2a/remote/v1/message:send")
        public ResponseEntity<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest req) {
            ServerCallContext ctx = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());
            EventKind result = requestHandler.onMessageSend(req.getParams(), ctx);
            return ResponseEntity.ok(new SendMessageResponse(req.getId(), result));
        }
    }

    @RestController
    public static class AgentCardController {
        @Value("${app.url}") private String appUrl;

        @GetMapping("/.well-known/agent-card.json")
        public AgentCard getAgentCard() {
            List<AgentSkill> skills = List.of(
                new AgentSkill.Builder().id("load-memory").name("load_memory")
                    .description("Load user profile and preferences").tags(List.of("memory","rag")).build(),
                new AgentSkill.Builder().id("save-memory").name("save_memory")
                    .description("Save transaction to long-term memory").tags(List.of("memory","rag")).build(),
                new AgentSkill.Builder().id("search-memory").name("search_memory")
                    .description("Semantic search over past conversations").tags(List.of("memory","rag")).build()
            );
            return new AgentCard.Builder()
                    .name(ROOT_AGENT.name()).description(ROOT_AGENT.description())
                    .url(appUrl + "/a2a/remote/v1/message:send").version("1.0.0")
                    .capabilities(new AgentCapabilities.Builder().streaming(false).build())
                    .defaultInputModes(List.of("text/plain")).defaultOutputModes(List.of("text/plain"))
                    .skills(skills).build();
        }
    }
}
