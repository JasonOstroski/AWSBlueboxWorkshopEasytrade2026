using System;
using EasyTrade.BrokerService.Helpers;

namespace BrokerService.test.UnitTests;

public class CircuitBreakerTests
{
    [Fact]
    public void ClosedByDefaultAndAllowsRequests()
    {
        var breaker = new CircuitBreaker(failureThreshold: 3, openDuration: TimeSpan.FromSeconds(10));

        Assert.Equal(CircuitBreaker.State.Closed, breaker.CurrentState);
        Assert.True(breaker.AllowRequest());
    }

    [Fact]
    public void OpensAfterConsecutiveFailures()
    {
        var breaker = new CircuitBreaker(failureThreshold: 3, openDuration: TimeSpan.FromSeconds(10));

        breaker.RecordFailure();
        breaker.RecordFailure();
        Assert.Equal(CircuitBreaker.State.Closed, breaker.CurrentState);

        breaker.RecordFailure(); // third failure trips it
        Assert.Equal(CircuitBreaker.State.Open, breaker.CurrentState);
        Assert.False(breaker.AllowRequest());
    }

    [Fact]
    public void SuccessResetsFailureCount()
    {
        var breaker = new CircuitBreaker(failureThreshold: 3, openDuration: TimeSpan.FromSeconds(10));

        breaker.RecordFailure();
        breaker.RecordFailure();
        breaker.RecordSuccess(); // resets the streak
        breaker.RecordFailure();
        breaker.RecordFailure();

        Assert.Equal(CircuitBreaker.State.Closed, breaker.CurrentState);
        Assert.True(breaker.AllowRequest());
    }

    [Fact]
    public void HalfOpensAfterCooldownAndClosesOnSuccess()
    {
        // Zero cool-down so the probe is allowed immediately after opening.
        var breaker = new CircuitBreaker(failureThreshold: 1, openDuration: TimeSpan.Zero);

        breaker.RecordFailure(); // opens
        Assert.Equal(CircuitBreaker.State.Open, breaker.CurrentState);

        // Cool-down elapsed -> allowed as a half-open probe.
        Assert.True(breaker.AllowRequest());
        Assert.Equal(CircuitBreaker.State.HalfOpen, breaker.CurrentState);

        // A successful probe closes the circuit again.
        breaker.RecordSuccess();
        Assert.Equal(CircuitBreaker.State.Closed, breaker.CurrentState);
    }

    [Fact]
    public void HalfOpenProbeFailureReopens()
    {
        var breaker = new CircuitBreaker(failureThreshold: 1, openDuration: TimeSpan.Zero);

        breaker.RecordFailure();     // open
        Assert.True(breaker.AllowRequest()); // half-open probe
        breaker.RecordFailure();     // probe fails -> reopen

        Assert.Equal(CircuitBreaker.State.Open, breaker.CurrentState);
    }
}
