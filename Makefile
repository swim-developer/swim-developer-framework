SONAR_URL    ?= http://localhost:9000
SONAR_TOKEN  ?=
NVD_API_KEY  ?=

-include .env.owasp
export NVD_API_KEY

PARENT_DIR  := $(abspath $(dir $(abspath $(lastword $(MAKEFILE_LIST))))/..)
GITHUB_SSH  := git@github.com:swim-developer

SYNC_DEPS := swim-developer-root

.PHONY: help sync pull pull-deps install-deps install test sonar sonar-up sonar-down security-deps

help:
	@echo ""
	@echo "  swim-developer-framework -- available targets"
	@echo "  ---------------------------------------------------"
	@echo ""
	@echo "    install            Build + install all modules to local Maven repo"
	@echo "    test               Unit + integration tests (Testcontainers)"
	@echo "    sonar-up           Start SonarQube at http://localhost:9000"
	@echo "    sonar              Run static analysis (requires sonar-up first)"
	@echo "    sonar-down         Stop SonarQube"
	@echo "    security-deps      OWASP Dependency-Check"
	@echo ""
	@echo "  Variables:"
	@echo "    SONAR_URL=$(SONAR_URL)"
	@echo "    SONAR_TOKEN=  (optional, leave empty for local SonarQube)"
	@echo ""
	@echo "  Sync:"
	@echo "    sync               Full setup: pull + pull-deps + install-deps"
	@echo "    pull               Pull this project from remote"
	@echo "    pull-deps          Clone missing deps + pull existing ones in $(PARENT_DIR)"
	@echo "    install-deps       Install all deps into local Maven repository"

sync: pull pull-deps install-deps

pull:
	@echo ""
	@echo "  ── Pull this project ────────────────────────────────────────"
	@git pull --ff-only
	@echo ""

pull-deps:
	@echo ""
	@echo "  ── Ensure sibling dependencies in $(PARENT_DIR) ─────────────"
	@for repo in $(SYNC_DEPS); do \
	  dir="$(PARENT_DIR)/$$repo"; \
	  if [ ! -d "$$dir" ]; then \
	    echo "  CLONE   $$repo"; \
	    git clone "$(GITHUB_SSH)/$$repo.git" "$$dir" --quiet; \
	  else \
	    printf "  PULL    $$repo ... "; \
	    git -C "$$dir" pull --ff-only --quiet 2>&1 && echo "ok" || echo "skipped (local changes or detached HEAD)"; \
	  fi; \
	done
	@echo ""

install-deps:
	@echo ""
	@echo "  ── Install dependencies into local Maven repository ─────────"
	@for repo in $(SYNC_DEPS); do \
	  dir="$(PARENT_DIR)/$$repo"; \
	  if [ ! -d "$$dir" ]; then \
	    echo "  SKIP    $$repo (not found — run: make pull-deps)"; \
	    continue; \
	  fi; \
	  mvn_cmd="mvn"; \
	  [ -f "$$dir/mvnw" ] && mvn_cmd="$$dir/mvnw"; \
	  if [ "$$repo" = "swim-developer-root" ]; then \
	    args="install -N -DskipTests -q"; \
	  else \
	    args="clean install -DskipTests -q"; \
	  fi; \
	  printf "  INSTALL $$repo ... "; \
	  "$$mvn_cmd" -f "$$dir/pom.xml" $$args && echo "ok" || { echo "FAIL"; exit 1; }; \
	done
	@echo ""
	@echo "  Done. Run: make install"
	@echo ""

install:
	./mvnw clean install -DskipTests

test:
	./mvnw verify -DskipITs=false

sonar-up:
	podman compose up -d sonarqube
	@echo "Waiting for SonarQube to be ready..."
	@until curl -sf http://localhost:9000/api/system/status | grep -q '"status":"UP"'; do sleep 3; done
	@echo "Granting anonymous scan permission..."
	@curl -sf -u admin:admin -X POST "http://localhost:9000/api/permissions/add_group" \
		-d "permission=scan&groupName=Anyone" > /dev/null || true
	@curl -sf -u admin:admin -X POST "http://localhost:9000/api/permissions/add_group" \
		-d "permission=provisioning&groupName=Anyone" > /dev/null || true
	@echo "SonarQube is up at http://localhost:9000"

sonar-down:
	podman compose down

sonar:
	./mvnw clean verify sonar:sonar \
		-DskipITs=false \
		-Dsonar.host.url=$(SONAR_URL) \
		$(if $(SONAR_TOKEN),-Dsonar.login=$(SONAR_TOKEN),) \
		-Dsonar.projectKey=swim-framework \
		-Dsonar.projectName=swim-framework

security-deps:
	@test -n "$(NVD_API_KEY)" || { echo ""; \
		echo "  WARNING: NVD_API_KEY not set."; \
		echo "  Without a key the NVD database download takes 1+ hours."; \
		echo "  Get a free key at https://nvd.nist.gov/developers/request-an-api-key"; \
		echo "  Then set it in .env.owasp or run: make security-deps NVD_API_KEY=<key>"; \
		echo ""; }
	./mvnw org.owasp:dependency-check-maven:aggregate \
		-DfailBuildOnCVSS=7 -Dformats=HTML,JSON -DskipTests \
		-DnvdApiKeyEnvironmentVariable=NVD_API_KEY \
		-DsuppressionFile=owasp-suppressions.xml
	@echo "Report: target/dependency-check-report.html"
