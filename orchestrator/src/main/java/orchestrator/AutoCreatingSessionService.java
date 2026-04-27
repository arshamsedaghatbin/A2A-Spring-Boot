//package orchestrator;
//
//import com.google.adk.events.Event;
//import com.google.adk.sessions.BaseSessionService;
//import com.google.adk.sessions.GetSessionConfig;
//import com.google.adk.sessions.InMemorySessionService;
//import com.google.adk.sessions.ListEventsResponse;
//import com.google.adk.sessions.ListSessionsResponse;
//import com.google.adk.sessions.Session;
//import io.reactivex.rxjava3.core.Completable;
//import io.reactivex.rxjava3.core.Maybe;
//import io.reactivex.rxjava3.core.Single;
//import java.util.Optional;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
///**
// * Wraps InMemorySessionService to auto-create sessions on getSession if not found.
// *
// * The ADK web UI generates session IDs client-side and sends them directly to
// * /run_sse_emitter without calling the session creation endpoint first.
// * This causes "Session not found" errors with the plain InMemorySessionService.
// *
// * Registered as a @Bean in ResilienceConfig to override AdkWebServer's default
// * sessionService() bean (requires spring.main.allow-bean-definition-overriding=true).
// */
//public class AutoCreatingSessionService implements BaseSessionService {
//
//    private static final Logger log = LoggerFactory.getLogger(AutoCreatingSessionService.class);
//    private final InMemorySessionService delegate = new InMemorySessionService();
//
//    @Override
//    public Single<Session> createSession(String appName, String userId,
//            ConcurrentMap<String, Object> state, String sessionId) {
//        return delegate.createSession(appName, userId, state, sessionId);
//    }
//
//    @Override
//    public Maybe<Session> getSession(String appName, String userId, String sessionId,
//            Optional<GetSessionConfig> config) {
//        return delegate.getSession(appName, userId, sessionId, config)
//                .switchIfEmpty(
//                    delegate.createSession(appName, userId, new ConcurrentHashMap<>(), sessionId)
//                            .doOnSuccess(s -> log.info(
//                                "[Session] Auto-created session {} for user {} in app {}",
//                                sessionId, userId, appName))
//                            .toMaybe()
//                );
//    }
//
//    @Override
//    public Single<ListSessionsResponse> listSessions(String appName, String userId) {
//        return delegate.listSessions(appName, userId);
//    }
//
//    @Override
//    public Completable deleteSession(String appName, String userId, String sessionId) {
//        return delegate.deleteSession(appName, userId, sessionId);
//    }
//
//    @Override
//    public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
//        return delegate.listEvents(appName, userId, sessionId);
//    }
//
//    @Override
//    public Single<Event> appendEvent(Session session, Event event) {
//        return delegate.appendEvent(session, event);
//    }
//}
