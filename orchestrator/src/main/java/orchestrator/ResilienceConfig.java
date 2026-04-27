//package orchestrator;
//
//import com.google.adk.sessions.BaseSessionService;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class ResilienceConfig {
//
//    /**
//     * Overrides AdkWebServer's default sessionService() bean.
//     * Auto-creates sessions on getSession so the ADK web UI never sees
//     * "Session not found" errors when it generates session IDs client-side.
//     *
//     * Requires: spring.main.allow-bean-definition-overriding=true
//     */
//    @Bean
//    public BaseSessionService sessionService() {
//        return new AutoCreatingSessionService();
//    }
//}
//
