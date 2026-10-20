.DEFAULT_GOAL := help
.PHONY: help generate tvctl style test check launcher install clean

JAVA_17 := $(shell /usr/libexec/java_home -v 17 2>/dev/null)
JAVA_HOME ?= $(or $(JAVA_17),/opt/homebrew/opt/openjdk@17)
GRADLE := cd apps/launcher && JAVA_HOME=$(JAVA_HOME) ./gradlew --console=plain

help: ## List the targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*## "} {printf "  %-10s %s\n", $$1, $$2}'

generate: ## Regenerate the Go and Java constants from contract.json
	cd packages/contract && go run ./cmd/generate

tvctl: ## Build tvctl into apps/tvctl/bin
	cd apps/tvctl && go build -trimpath -ldflags=-s -o bin/tvctl ./cmd/tvctl

style: ## gofmt and the 100-character line limit
	scripts/check-style

test: style ## Vet and test tvctl, the debloat lists and the themes
	cd apps/tvctl && go vet ./... && go test ./...

GENERATED := apps/tvctl/internal/contract \
	apps/launcher/app/src/main/java/com/gathin/tvhome/Contract.java

check: generate test ## Everything CI runs: contract drift, tvctl, launcher tests and lint
	git diff --exit-code -- $(GENERATED)
	$(GRADLE) testReleaseUnitTest lintRelease

launcher: ## Build the release APK (apps/launcher/app/build/outputs/apk/release/km-launcher.apk)
	$(GRADLE) assembleRelease

install: ## Build and install the launcher on $TV_HOST, making it Home
	@test -n "$$TV_HOST" || (echo "set TV_HOST to the TV's IP" && exit 1)
	apps/launcher/build-and-install.sh --input-key-service

clean: ## Remove build output
	rm -rf apps/tvctl/bin apps/launcher/app/build apps/launcher/build
