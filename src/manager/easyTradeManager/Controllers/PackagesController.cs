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
    public class PackagesController : ControllerBase
    {
        // Packages are static reference/catalog data. Same rationale as
        // ProductsController: the unfiltered full-table SELECT on [Packages]
        // per request is a top DB-load driver; cache it to survive 100x.
        private const string PackagesCacheKey = "manager:packages:all";
        private static readonly TimeSpan CatalogCacheTtl = TimeSpan.FromSeconds(60);

        private readonly PackagesDbContext _context;
        private readonly IMemoryCache _cache;
        private readonly ILogger _logger;

        public PackagesController(PackagesDbContext context, IMemoryCache cache, ILogger<PackagesController> logger)
        {
            _context = context;
            _cache = cache;
            _logger = logger;
        }

        // GET: api/Packages/GetPackageById/2
        [HttpGet("GetPackageById/{id}")]
        public async Task<ActionResult<Package>> GetPackageById(int id)
        {
            _logger.LogInformation("Getting package with ID [{id}]", id);

            var package = await _context.Packages.FindAsync(id);

            if (package == null)
            {
                _logger.LogWarning("Package with ID [{id}] not found", id);
                return NotFound();
            }

            return package;
        }

        // GET: api/Packages/GetPackages
        [HttpGet("GetPackages")]
        public async Task<ActionResult<IEnumerable<Package>>> GetPackages()
        {
            _logger.LogInformation("Getting all packages");

            if (_cache.TryGetValue(PackagesCacheKey, out List<Package> cached))
            {
                return cached;
            }

            var packages = await _context.Packages.AsNoTracking().ToListAsync();
            _cache.Set(PackagesCacheKey, packages, new MemoryCacheEntryOptions
            {
                AbsoluteExpirationRelativeToNow = CatalogCacheTtl
            });
            return packages;
        }
    }
}
