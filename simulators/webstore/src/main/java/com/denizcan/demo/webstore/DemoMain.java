package com.denizcan.demo.webstore;
import com.denizcan.demo.channel.ChannelApplication;
import org.springframework.boot.SpringApplication;
import java.util.Map;
public class DemoMain {
 public static void main(String[] args) {
  SpringApplication app = new SpringApplication(ChannelApplication.class);
  app.setDefaultProperties(Map.of("server.port", "8084", "demo.source", "WEBSTORE", "spring.application.name", "webstore-simulator"));
  app.run(args);
 }
}