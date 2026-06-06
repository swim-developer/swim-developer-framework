# swim-framework-leader-kubernetes

Leader election adapter using the Kubernetes Lease API. Implements `LeaderElectionStrategy` from `swim-framework-core`.

In a multi-replica deployment, only the leader executes scheduled tasks (heartbeat publishing, subscription expiry, outbox recovery). This module uses the native Kubernetes Lease resource for leader election, no external dependencies beyond the cluster itself.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `swim.leader-election.lease-name` | `swim-leader` | Lease resource name |
| `swim.leader-election.lease-namespace` |: | Kubernetes namespace |
| `swim.leader-election.lease-duration-seconds` | `15` | Lease duration |
| `swim.leader-election.renew-deadline-seconds` | `10` | Renewal deadline |
| `swim.leader-election.retry-period-seconds` | `2` | Retry interval |

## Dependencies

- `swim-framework-core`
- Quarkus Kubernetes Client
