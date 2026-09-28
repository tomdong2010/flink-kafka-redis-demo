.PHONY: help up down logs test package smoke restart-taskmanager poison

help: ## Show this help
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

up: ## Build and start everything (dashboard :8088, Flink UI :8081)
	docker compose up -d --build

down: ## Stop everything and delete volumes
	docker compose --profile ui down -v

logs: ## Follow producer, job and dashboard logs
	docker compose logs -f producer jobmanager taskmanager dashboard

test: ## Unit and integration tests (Redis tests need Docker)
	mvn -B verify

package: ## Build the job jar into target/
	mvn -B -q package -DskipTests

smoke: ## Check a running stack end to end
	scripts/smoke-test.sh

restart-taskmanager: ## Crash the TaskManager and watch the job recover from its last checkpoint
	docker compose restart -t 0 taskmanager
	@echo "TaskManager killed and restarted; the job restores its last checkpoint. Watch http://localhost:8081"

poison: ## Send a malformed record; the job skips it and counts it in malformedEvents
	echo 'this is {not json' | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic user-events
