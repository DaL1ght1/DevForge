package com.example.devforge.service;

public interface GithubWebhookService {

  void handle(String event, String deliveryId, String signature, byte[] body);
}
