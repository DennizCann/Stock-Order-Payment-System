package com.denizcan.demo.branch;
import com.denizcan.demo.channel.ChannelApplication;
import org.springframework.boot.SpringApplication;
import java.util.Map;
public class DemoMain {
 public static void main(String[] args) {
  SpringApplication app = new SpringApplication(ChannelApplication.class);
  app.setDefaultProperties(Map.of("server.port", "8085", "demo.source", "BRANCH", "spring.application.name", "branch-simulator"));
  app.run(args);
 }
}