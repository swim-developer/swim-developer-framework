SONAR_URL    ?= http://localhost:9000
SONAR_TOKEN  ?=
NVD_API_KEY  ?=

-include .env.owasp
export NVD_API_KEY

.PHONY: help install test sonar sonar-up sonar-down security-deps

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
