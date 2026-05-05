# swim-framework-leader-infinispan

Leader election adapter using Infinispan distributed cache. Implements `LeaderElectionStrategy` from `swim-framework-core`.

An alternative to the Kubernetes Lease adapter for environments where Infinispan is already available. Uses `putIfAbsent` with TTL on a shared cache key for leader election.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `swim.leader-election.lock-ttl-seconds` | `15` | Lock expiration |
| `swim.leader-election.refresh-interval-seconds` | `5` | Refresh attempt interval |
| `HOSTNAME` | `local-0` | Instance identifier |

## Dependencies

- `swim-framework-core`
- Quarkus Infinispan Client
