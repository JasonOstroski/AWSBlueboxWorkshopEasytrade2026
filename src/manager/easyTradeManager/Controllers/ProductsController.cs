using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using easyTradeManager.Models;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Logging;

namespace easyTradeManager.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class ProductsController : ControllerBase
    {
        // Products are static reference/catalog data. Bluebox showed manager
        // driving ~64% of all shared-DB load, dominated by an unfiltered
        // full-table SELECT on [Products] run on nearly every request. Caching
        // the catalog collapses that per-request scan to one query per TTL,
        // which is the single largest DB-load reduction for a 100x scale-up.
        private const string ProductsCacheKey = "manager:products:all";
        private static readonly TimeSpan CatalogCacheTtl = TimeSpan.FromSeconds(60);

        private readonly ProductsDbContext _context;
        private readonly IMemoryCache _cache;
        private readonly ILogger _logger;

        public ProductsController(ProductsDbContext context, IMemoryCache cache, ILogger<ProductsController> logger)
        {
            _context = context;
            _cache = cache;
            _logger = logger;
        }

        // GET: api/Products/GetProductById/2
        [HttpGet("GetProductById/{id}")]
        public async Task<ActionResult<Product>> GetProductById(int id)
        {
            _logger.LogInformation("Getting package with ID [{id}]", id);
            var product = await _context.Products.FindAsync(id);

            if (product == null)
            {
                _logger.LogWarning("Package with ID [{id}] not found", id);
                return NotFound();
            }

            return product;
        }

        // GET: api/Products/GetProducts
        [HttpGet("GetProducts")]
        public async Task<ActionResult<IEnumerable<Product>>> GetProducts()
        {
            _logger.LogInformation("Getting all products");

            if (_cache.TryGetValue(ProductsCacheKey, out List<Product> cached))
            {
                return cached;
            }

            var products = await _context.Products.AsNoTracking().ToListAsync();
            _cache.Set(ProductsCacheKey, products, new MemoryCacheEntryOptions
            {
                AbsoluteExpirationRelativeToNow = CatalogCacheTtl
            });
            return products;
        }
    }
}
