using System.Net;
using EasyTrade.BrokerService.Entities.Prices.DTO;
using EasyTrade.BrokerService.Helpers;

namespace EasyTrade.BrokerService.Entities.Prices.ServiceConnector;

public class PriceServiceConnector(
    IConfiguration configuration,
    IHttpClientFactory httpClientFactory,
    ILogger<PriceServiceConnector> logger
) : IPriceServiceConnector
{
    /// <summary>
    /// Named client whose timeout + resilience are configured centrally in
    /// <see cref="Helpers.BrokerServiceDependencyExtension"/>. Using the named
    /// client (instead of a bare CreateClient()) is what enforces the bounded
    /// per-attempt timeout on the pricing-service call.
    /// </summary>
    public const string PriceHttpClientName = "pricing-service";

    // Process-wide circuit breaker for the pricing-service dependency: after a
    // run of failures/timeouts it opens and broker-service fails fast instead of
    // queuing requests behind a slow downstream (the 100x saturation risk).
    private static readonly CircuitBreaker PricingCircuit = new(
        failureThreshold: 5,
        openDuration: TimeSpan.FromSeconds(15)
    );

    private readonly IConfiguration _configuration = configuration;
    private readonly IHttpClientFactory _httpClientFactory = httpClientFactory;
    private readonly ILogger _logger = logger;

    private string PriceServiceUrl => $"http://{_configuration[Constants.PricingService]}/";

    public async Task<IEnumerable<Price>> GetPricesByInstrumentId(int id, int count = 10)
    {
        _logger.LogInformation(
            "Fetching prices with instrument ID [{id}], count [{count}]",
            id,
            count
        );

        var endpoint = $"v1/prices/instrument/{id}?records={count}";
        return await FetchPrices(endpoint);
    }

    public async Task<IEnumerable<Price>> GetLatestPrices()
    {
        _logger.LogInformation("Fetching latest prices");

        const string endpoint = "v1/prices/latest";
        return await FetchPrices(endpoint);
    }

    public async Task<Price?> GetLastPriceByInstrumentId(int id)
    {
        var priceArray = await GetPricesByInstrumentId(id, 1);
        return priceArray.FirstOrDefault();
    }

    private async Task<IEnumerable<Price>> FetchPrices(string endpoint)
    {
        // Fail fast when pricing-service is already known-unhealthy.
        if (!PricingCircuit.AllowRequest())
        {
            _logger.LogWarning(
                "Pricing-service circuit is OPEN; short-circuiting call to [{endpoint}]",
                endpoint
            );
            return Array.Empty<Price>();
        }

        try
        {
            using var client = GetHttpClient();
            using var response = await client.GetAsync(endpoint);
            if (response.StatusCode == HttpStatusCode.OK)
            {
                var pricesResult = await response.Content.ReadFromJsonAsync<PricesResultDto>();
                var prices = pricesResult?.Results!;
                _logger.LogDebug("Fetched prices: {content}", prices.ToJson());
                PricingCircuit.RecordSuccess();
                return prices;
            }

            // A 5xx from pricing-service counts against the breaker; a 4xx is a
            // client-side problem and should not trip it.
            if ((int)response.StatusCode >= 500)
            {
                PricingCircuit.RecordFailure();
            }
            _logger.LogError("Fetch failed with status code [{statusCode}]", response.StatusCode);
            return Array.Empty<Price>();
        }
        catch (TaskCanceledException ex)
        {
            // Bounded HttpClient.Timeout surfaces as TaskCanceledException.
            PricingCircuit.RecordFailure();
            _logger.LogError(ex, "Pricing-service call to [{endpoint}] timed out", endpoint);
            return Array.Empty<Price>();
        }
        catch (HttpRequestException ex)
        {
            PricingCircuit.RecordFailure();
            _logger.LogError(ex, "Pricing-service call to [{endpoint}] failed", endpoint);
            return Array.Empty<Price>();
        }
    }

    private HttpClient GetHttpClient()
    {
        // Named client carries the bounded timeout configured at startup.
        var client = _httpClientFactory.CreateClient(PriceHttpClientName);
        client.BaseAddress = new Uri(PriceServiceUrl);
        return client;
    }
}
