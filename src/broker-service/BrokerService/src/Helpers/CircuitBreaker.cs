namespace EasyTrade.BrokerService.Helpers;

/// <summary>
/// Minimal thread-safe circuit breaker (no external dependency).
///
/// Rationale: Bluebox observed the broker-service -> pricing-service call has no
/// timeout budget or circuit breaker. When pricing-service slowed, broker-service's
/// own p95 blew up to ~11.8s at &lt;1 req/s — classic thread/connection queuing.
/// Under 100x that call chain saturates long before CPU or the DB. A circuit
/// breaker makes broker-service fail fast (and shed load) instead of piling up
/// requests behind a slow/hung downstream.
///
/// States: Closed (normal) -> Open (short-circuit, fail fast) after
/// <see cref="_failureThreshold"/> consecutive failures -> HalfOpen (probe) after
/// <see cref="_openDuration"/> -> Closed on a successful probe.
/// </summary>
public sealed class CircuitBreaker
{
    public enum State { Closed, Open, HalfOpen }

    private readonly int _failureThreshold;
    private readonly TimeSpan _openDuration;
    private readonly object _gate = new();

    private int _consecutiveFailures;
    private State _state = State.Closed;
    private DateTime _openedAtUtc;

    public CircuitBreaker(int failureThreshold, TimeSpan openDuration)
    {
        _failureThreshold = failureThreshold < 1 ? 1 : failureThreshold;
        _openDuration = openDuration;
    }

    public State CurrentState
    {
        get { lock (_gate) { return _state; } }
    }

    /// <summary>
    /// Returns true if a call is currently permitted. When Open, transitions to
    /// HalfOpen (allowing a single probe) once the cool-down has elapsed.
    /// </summary>
    public bool AllowRequest()
    {
        lock (_gate)
        {
            if (_state == State.Open)
            {
                if (DateTime.UtcNow - _openedAtUtc >= _openDuration)
                {
                    _state = State.HalfOpen;
                    return true;
                }
                return false;
            }
            return true;
        }
    }

    public void RecordSuccess()
    {
        lock (_gate)
        {
            _consecutiveFailures = 0;
            _state = State.Closed;
        }
    }

    public void RecordFailure()
    {
        lock (_gate)
        {
            _consecutiveFailures++;
            if (_state == State.HalfOpen || _consecutiveFailures >= _failureThreshold)
            {
                _state = State.Open;
                _openedAtUtc = DateTime.UtcNow;
            }
        }
    }
}

/// <summary>Thrown when the circuit is open and a call is short-circuited.</summary>
public sealed class CircuitOpenException : Exception
{
    public CircuitOpenException(string message) : base(message) { }
}
