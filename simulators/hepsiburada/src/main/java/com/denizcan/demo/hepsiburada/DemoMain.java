package com.denizcan.demo.hepsiburada;
import com.denizcan.demo.channel.ChannelApplication;
import org.springframework.boot.SpringApplication;
import java.util.Map;
public class DemoMain {
 public static void main(String[] args) {
  SpringApplication app = new SpringApplication(ChannelApplication.class);
  app.setDefaultProperties(Map.of("server.port", "8082", "demo.source", "HEPSIBURADA", "spring.application.name", "hepsiburada-simulator"));
  app.run(args);
 }
}