package com.example.devforge.controller;

import com.example.devforge.service.GithubWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/github/webhooks")
public class GithubWebhookController {

  private final GithubWebhookService githubWebhookService;

  @SecurityRequirements
  @Operation(summary = "GitHub App webhook receiver (called by GitHub)")
  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void receive(
      @RequestHeader(name = "X-GitHub-Event", required = false) String event,
      @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
      @RequestBody byte[] body) {
    githubWebhookService.handle(event, deliveryId, signature, body);
  }
}
