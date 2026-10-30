package com.denizcan.demo.trendyol;
import com.denizcan.demo.channel.ChannelApplication;
import org.springframework.boot.SpringApplication;
import java.util.Map;
public class DemoMain {
 public static void main(String[] args) {
  SpringApplication app = new SpringApplication(ChannelApplication.class);
  app.setDefaultProperties(Map.of("server.port", "8081", "demo.source", "TRENDYOL", "spring.application.name", "trendyol-simulator"));
  app.run(args);
 }
}