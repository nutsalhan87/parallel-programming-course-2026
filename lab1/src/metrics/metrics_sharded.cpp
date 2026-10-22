#include "metrics_sharded.hpp"
#include "metrics.hpp"
#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>

void MetricsCollectorsSharded::record(uint64_t value)
{
    auto idx = std::max(std::min(value / BUCKET_STEP, BUCKETS - 1ul), 0ul);
    {
        auto lock = std::lock_guard<std::mutex>(this->mutexes[idx % MUTEXES]);
        this->buckets[idx]++;
    }

    this->count.fetch_add(1);
    this->sum.fetch_add(value);

    uint64_t m;
    uint64_t current = this->min.load(std::memory_order_relaxed);
    do {
        m = std::min(current, value);
    } while (!this->min.compare_exchange_weak(current, m,
        std::memory_order_release));
    current = this->max.load(std::memory_order_relaxed);
    do {
        m = std::max(current, value);
    } while (!this->max.compare_exchange_weak(current, m,
        std::memory_order_release));
}

Snapshot MetricsCollectorsSharded::snapshot()
{
    std::array<uint64_t, BUCKETS> buckets = {};
    for (std::size_t i = 0; i < MUTEXES; ++i) {
        auto lock = std::lock_guard<std::mutex>(this->mutexes[i]);
        for (std::size_t j = i; j < BUCKETS; j += MUTEXES) {
            buckets[j] = this->buckets[j];
        }
    }

    uint64_t count = this->count.load();
    uint64_t sum = this->sum.load();
    uint64_t min = this->min.load();
    uint64_t max = this->max.load();

    uint64_t p50 = cum_search<50>(buckets, count);
    uint64_t p99 = cum_search<99>(buckets, count);

    return { buckets, count, sum, min, max, p50, p99 };
}

const std::string MetricsCollectorsSharded::name()
{
    return "Sharded Metrics Collector";
}