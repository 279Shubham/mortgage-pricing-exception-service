PORT ?= 8080
IMAGE ?= pricing-exception-service
CONTAINER ?= pricing-exception-service

.PHONY: build run stop test

build:
	docker build -t $(IMAGE) .

run: build
	docker run --rm --name $(CONTAINER) -p $(PORT):8080 $(IMAGE)

stop:
	docker stop $(CONTAINER)

test:
	docker build --target test --progress=plain --no-cache-filter test .
