package com.denizcan.demo.n11;
import com.denizcan.demo.channel.ChannelApplication;
import org.springframework.boot.SpringApplication;
import java.util.Map;
public class DemoMain {
 public static void main(String[] args) {
  SpringApplication app = new SpringApplication(ChannelApplication.class);
  app.setDefaultProperties(Map.of("server.port", "8083", "demo.source", "N11", "spring.application.name", "n11-simulator"));
  app.run(args);
 }
}