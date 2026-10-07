package kafka

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/git"
	"devforge/provisioner/internal/github"
	"devforge/provisioner/internal/model"

	kafka "github.com/segmentio/kafka-go"
)

type ProvisionWorker struct {
	reader          *kafka.Reader
	writer          *kafka.Writer
	gen             *generator.ProjectGenerator
	ghClient        *github.Client
	gitPusher       *git.Pusher
	outputWorkspace string
}

func NewProvisionWorker(
	brokers []string,
	reqTopic string,
	respTopic string,
	groupID string,
	gen *generator.ProjectGenerator,
	ghClient *github.Client,
	gitPusher *git.Pusher,
	workspace string,
) *ProvisionWorker {
	ensureTopics(brokers, reqTopic, respTopic)

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:  brokers,
		GroupID:  groupID,
		Topic:    reqTopic,
		MinBytes: 10e3,
		MaxBytes: 10e6,
	})

	writer := &kafka.Writer{
		Addr:     kafka.TCP(brokers...),
		Topic:    respTopic,
		Balancer: &kafka.LeastBytes{},
	}

	return &ProvisionWorker{
		reader:          reader,
		writer:          writer,
		gen:             gen,
		ghClient:        ghClient,
		gitPusher:       gitPusher,
		outputWorkspace: workspace,
	}
}

func ensureTopics(brokers []string, topics ...string) {
	if len(brokers) == 0 || len(topics) == 0 {
		return
	}

	checkCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	dialer := &kafka.Dialer{Timeout: 10 * time.Second}
	conn, err := dialer.DialLeader(checkCtx, "tcp", brokers[0], topics[0], 0)
	if err == nil {
		_ = conn.Close()
		return
	}

	conn, err = dialer.DialContext(checkCtx, "tcp", brokers[0])
	if err != nil {
		log.Printf("[Kafka Worker] Topic check failed: %v", err)
		return
	}
	defer func() {
		_ = conn.Close()
	}()

	configs := make([]kafka.TopicConfig, 0, len(topics))
	for _, topic := range topics {
		configs = append(configs, kafka.TopicConfig{
			Topic:             topic,
			NumPartitions:     1,
			ReplicationFactor: 1,
		})
	}
	if err := conn.CreateTopics(configs...); err != nil {
		log.Printf("[Kafka Worker] Topic creation/check failed: %v", err)
	}
}

func (w *ProvisionWorker) Start(ctx context.Context) {
	log.Printf("[Kafka Worker] Subscribed to topic: %s", w.reader.Config().Topic)
	for {
		select {
		case <-ctx.Done():
			log.Println("[Kafka Worker] Shutting down...")
			return
		default:
			msg, err := w.reader.ReadMessage(ctx)
			if err != nil {
				if ctx.Err() != nil {
					return
				}
				log.Printf("[Kafka Worker] Read error: %v", err)
				select {
				case <-ctx.Done():
					return
				case <-time.After(time.Second):
				}
				continue
			}

			var req model.ProvisionRequest
			if err := json.Unmarshal(msg.Value, &req); err != nil {
				log.Printf("[Kafka Worker] Malformed JSON request: %v", err)
				continue
			}

			log.Printf("[Kafka Worker] Processing service [%s] (ID: %s)", req.ServiceName, req.ServiceID)
			resp := w.executeProvision(ctx, req)
			if resp.Status == "FAILED" {
				log.Printf("[Kafka Worker] Provisioning failed for service [%s] (ID: %s): %s",
					resp.ServiceName, resp.ServiceID, resp.ErrorMessage)
			}
			w.publishResponse(ctx, resp)
		}
	}
}

func (w *ProvisionWorker) executeProvision(ctx context.Context, req model.ProvisionRequest) model.ProvisionResponse {
	resp := model.ProvisionResponse{
		ServiceID:   req.ServiceID,
		ServiceName: req.ServiceName,
		Status:      "FAILED",
	}

	workDir, err := os.MkdirTemp(w.outputWorkspace, "provision-*")
	if err != nil {
		resp.ErrorMessage = fmt.Sprintf("workDir creation failed: %v", err)
		return resp
	}
	defer func(path string) {
		err := os.RemoveAll(path)
		if err != nil {
			log.Printf("[Kafka Worker] Failed to clean up workDir [%s]: %v", path, err)
		}
	}(workDir)

	genReq := model.GenerationRequest{
		ServiceName:  req.ServiceName,
		TemplateName: req.TemplateName,
		PackageName:  req.PackageName,
		ClassName:    req.ClassName,
		Description:  req.Description,
		DatabaseType: req.DatabaseType,
	}

	outPath, err := w.gen.Generate(genReq, workDir)
	if err != nil {
		resp.ErrorMessage = fmt.Sprintf("generator failed: %v", err)
		return resp
	}

	cloneURL, htmlURL, err := w.ghClient.CreateRepository(ctx, req.ServiceName, req.Description)
	if err != nil {
		resp.ErrorMessage = fmt.Sprintf("github repo creation failed: %v", err)
		return resp
	}

	if err := w.gitPusher.PushDirectory(ctx, outPath, cloneURL); err != nil {
		resp.ErrorMessage = fmt.Sprintf("git push failed: %v", err)
		return resp
	}

	resp.Status = "COMPLETED"
	resp.RepositoryURL = htmlURL
	return resp
}

func (w *ProvisionWorker) publishResponse(ctx context.Context, resp model.ProvisionResponse) {
	payload, err := json.Marshal(resp)
	if err != nil {
		log.Printf("[Kafka Worker] Serialization error: %v", err)
		return
	}

	err = w.writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(resp.ServiceID),
		Value: payload,
	})
	if err != nil {
		log.Printf("[Kafka Worker] Failed to send response for service [%s]: %v", resp.ServiceName, err)
	} else {
		log.Printf("[Kafka Worker] Published completion event for [%s] with status [%s]", resp.ServiceName, resp.Status)
	}
}

func (w *ProvisionWorker) Close() {
	_ = w.reader.Close()
	_ = w.writer.Close()
}
