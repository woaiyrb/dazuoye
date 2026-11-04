/**
 * HealthService - Application Health Check Service
 *
 * Provides health check endpoints for container orchestration platforms.
 * Implements both liveness and readiness probes following Kubernetes standards:
 * - Liveness: Indicates if the application should be restarted
 * - Readiness: Indicates if the application is ready to receive traffic
 *
 * Integrates with NestJS Terminus for standardized health check responses.
 *
 * @module health
 */

import { Injectable } from "@nestjs/common";
import { HealthCheckResult, HealthCheckService } from "@nestjs/terminus";
import {
  GithubCredentialCheckService,
  GithubCredentialState,
} from "../api/github/github-credential-check.service";
import {
  DeepTutorClientService,
  DeepTutorHealthState,
} from "../api/llm/core/services/deeptutor-client.service";
import { DatabaseHealthIndicator } from "../database/health/database-health.indicator";

export interface IntegrationsReport {
  status: "ok" | "degraded";
  checks: {
    name: string;
    state: GithubCredentialState | DeepTutorHealthState;
    checkedAt: string | null;
    latencyMs?: number;
    error?: string;
  }[];
}

const DEGRADED_STATES = new Set<GithubCredentialState | DeepTutorHealthState>([
  "misconfigured",
  "not_configured",
  "unreachable",
]);

@Injectable()
export class HealthService {
  constructor(
    private readonly health: HealthCheckService,
    private readonly githubCredentials: GithubCredentialCheckService,
    private readonly deepTutor: DeepTutorClientService,
    private readonly database: DatabaseHealthIndicator,
  ) {}

  /**
   * Configuration-level health of outbound integrations. Returns 200 whatever
   * the outcome — this is for operators and alerting, not for the orchestrator.
   */
  async checkIntegrations(): Promise<IntegrationsReport> {
    const github = this.githubCredentials.getStatus();
    const deepTutor = await this.deepTutor.checkReadiness(
      Number(process.env.DEEPTUTOR_HEALTH_TIMEOUT_MS) || 3000,
    );
    const checks: IntegrationsReport["checks"] = [
      {
        name: "github_oauth",
        state: github.state,
        checkedAt: github.checkedAt,
      },
      {
        name: "deeptutor_backend",
        state: deepTutor.state,
        checkedAt: deepTutor.checkedAt,
        latencyMs: deepTutor.latencyMs,
        error: deepTutor.error,
      },
    ];
    return {
      status: checks.some((check) => DEGRADED_STATES.has(check.state))
        ? "degraded"
        : "ok",
      checks,
    };
  }

  /**
   * Basic health probe - verifies the API is responsive without
   * checking external dependencies.
   *
   * @returns {Promise<HealthCheckResult>} Health status
   */
  checkHealth(): Promise<HealthCheckResult> {
    return this.health.check([]);
  }

  /**
   * Readiness probe - checks if the application is ready to receive traffic
   * Only checks critical dependencies required for handling requests
   *
   * @returns {Promise<HealthCheckResult>} Readiness status
   */
  checkReadiness(): Promise<HealthCheckResult> {
    return this.health.check([() => this.database.checkDatabase("database")]);
  }

  /**
   * Liveness probe - checks if the application is alive and functioning
   * Does not check external dependencies
   *
   * @returns {Promise<HealthCheckResult>} Liveness status
   */
  checkLiveness(): Promise<HealthCheckResult> {
    return this.checkHealth();
  }
}
