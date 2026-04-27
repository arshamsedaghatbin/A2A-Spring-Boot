package orchestrator;

import com.google.adk.a2a.agent.RemoteA2AAgent;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.adk.web.AgentLoader;
import com.google.adk.web.AgentStaticLoader;
import io.a2a.client.Client;
import io.a2a.client.http.JdkA2AHttpClient;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "orchestrator")
public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private static final String AUTH_BASE_URL     = "http://localhost:8081";
    private static final String BALANCE_BASE_URL  = "http://localhost:8082";
    private static final String TRANSFER_BASE_URL = "http://localhost:8083";

    // ================================================================
    // Tool 1: exit loop وقتی همه اطلاعات کامله
    // ================================================================
    public static Map<String, Object> exitLoop(
            @Schema(name = "toolContext") ToolContext toolContext) {
        toolContext.eventActions().setEscalate(true);
        return Map.of("status", "complete", "message", "All info collected. Exiting loop.");
    }

    // ================================================================
    // Tool 2: pause loop and return question to user to get their answer
    // ================================================================
    public static Map<String, Object> pauseForUserInput(
            @Schema(name = "toolContext") ToolContext toolContext) {
        toolContext.eventActions().setEscalate(true);
        return Map.of("status", "waiting", "message", "Waiting for user input.");
    }

    // ================================================================
    // Remote A2A Agent builder
    // ================================================================
    private static RemoteA2AAgent createRemoteAgent(String name, String description, String baseUrl) {
        AgentCard card = new AgentCard.Builder()
                .name(name).description(description)
                .url(baseUrl + "/a2a/remote/v1/message:send").version("1.0.0")
                .capabilities(new AgentCapabilities.Builder().streaming(false).build())
                .defaultInputModes(List.of("text/plain")).defaultOutputModes(List.of("text/plain"))
                .skills(List.of()).build();
        Client client = Client.builder(card)
                .withTransport(JSONRPCTransport.class,
                        new JSONRPCTransportConfigBuilder().httpClient(new JdkA2AHttpClient()).build())
                .build();
        log.info("Registered remote agent: {} -> {}", name, baseUrl);
        return RemoteA2AAgent.builder().name(card.name()).agentCard(card).a2aClient(client).build();
    }

    // ================================================================
    // Root Agent
    // ================================================================
    @Bean @Lazy
    public LlmAgent rootLlmAgent(ObservabilityCallback observabilityCallback,
                                  LangfusePromptService promptService) {

        // Quality evaluator — چک می‌کنه همه چیز هست، اگه بود exit_loop صدا میزنه
        LlmAgent qualityEvaluator = LlmAgent.builder()
                .name("quality-evaluator")
                .model("gemini-2.5-flash")
                .description("Checks if all required banking fields are present.")
                .instruction(promptService.getPrompt("quality-evaluator"))
                .tools(FunctionTool.create(Agent.class, "exitLoop"))
                .build();

        // Prompt enhancer — از کاربر اطلاعات می‌خواد
        LlmAgent promptEnhancer = LlmAgent.builder()
                .name("prompt-enhancer")
                .model("gemini-2.5-flash")
                .description("Asks user for missing banking information.")
                .instruction(promptService.getPrompt("prompt-enhancer"))
                .tools(FunctionTool.create(Agent.class, "pauseForUserInput"))
                .build();

        // LoopAgent با local sub-agents
        BaseAgent infoCollector = LoopAgent.builder()
                .name("bank-info-collector")
                .description("Collects all required info before processing")
                .maxIterations(5)
                .subAgents(qualityEvaluator, promptEnhancer)
                .build();

        // Remote execution agents
        RemoteA2AAgent authAgent     = createRemoteAgent("auth-agent",     "Authenticates the user",   AUTH_BASE_URL);
        RemoteA2AAgent balanceAgent  = createRemoteAgent("balance-agent",  "Checks account balance",   BALANCE_BASE_URL);
        RemoteA2AAgent transferAgent = createRemoteAgent("transfer-agent", "Executes money transfers", TRANSFER_BASE_URL);

        return LlmAgent.builder()
                .name("bank-orchestrator")
                .model("gemini-2.5-flash")
                .description("Bank orchestrator with iterative refinement.")
                .beforeAgentCallbackSync(observabilityCallback.beforeAgent())
                .afterAgentCallbackSync(observabilityCallback.afterAgent())
                .beforeModelCallbackSync(observabilityCallback.beforeModel())
                .afterModelCallbackSync(observabilityCallback.afterModel())
                .beforeToolCallbackSync(observabilityCallback.beforeTool())
                .afterToolCallbackSync(observabilityCallback.afterTool())
                .instruction("""
            You are a bank orchestrator. Follow these steps IN ORDER:

            STEP 1 — Delegate to bank-info-collector to collect all required info.
              - bank-info-collector will ask the user for missing fields one at a time.
              - If it returns a question (status: waiting), pass that question directly
                to the user and wait for their reply.
              - On each new user message, delegate to bank-info-collector again
                until it returns status: complete.

            STEP 2 — Once bank-info-collector returns complete:
              - Delegate to auth-agent to verify userId
              - Delegate to balance-agent to check fromAccount balance
              - Delegate to transfer-agent to execute the transfer

            STEP 3 — Report result clearly with transaction ID.

            For balance-only requests: go directly to balance-agent.
            For auth-only requests: go directly to auth-agent.
            """)
                .subAgents(infoCollector, authAgent, balanceAgent, transferAgent)
                .build();
    }

    @Bean @Lazy public BaseAgent rootAgent(LlmAgent a) { return a; }
    @Bean @Lazy public AgentLoader agentLoader(LlmAgent a) { return new AgentStaticLoader(a); }

    @RestController
    public static class AgentCardController {
        @Value("${app.url}") private String appUrl;
        @Value("${agent.name:bank-orchestrator}") private String agentName;
        @Value("${agent.description:Bank orchestrator with iterative refinement.}") private String agentDescription;

        @GetMapping("/.well-known/agent-card.json")
        public AgentCard getAgentCard() {
            return new AgentCard.Builder().name(agentName).description(agentDescription)
                    .url(appUrl + "/a2a/remote/v1/message:send").version("1.1.0")
                    .capabilities(new AgentCapabilities.Builder().streaming(false).build())
                    .defaultInputModes(List.of("text/plain")).defaultOutputModes(List.of("text/plain"))
                    .skills(List.of(new AgentSkill.Builder().id("bank-transfer").name("bank-transfer")
                            .description("Full transfer with iterative info collection")
                            .tags(List.of("orchestrator","banking")).build()))
                    .build();
        }
    }
}
