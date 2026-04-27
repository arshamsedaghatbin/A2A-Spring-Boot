package authagent;

import com.google.adk.web.AdkWebServer;
import org.springframework.boot.SpringApplication;

public class Main {
  public static void main(String[] args) {
    System.setProperty("adk.agents.loader", "static");
    new SpringApplication(AdkWebServer.class, Agent.class).run(args);
  }
}
