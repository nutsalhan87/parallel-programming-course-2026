#include "metrics_baseline.hpp"
#include "metrics.hpp"
#include <array>
#include <cstdint>

void MetricsCollectorsBaseline::record(uint64_t value)
{
    auto idx = std::max(std::min(value / BUCKET_STEP, BUCKETS - 1ul), 0ul);
    this->buckets[idx]++;
    this->count++;
    this->sum += value;
    this->min = std::min(this->min, value);
    this->max = std::max(this->max, value);
}

Snapshot MetricsCollectorsBaseline::snapshot()
{
    auto buckets = this->buckets;
    uint64_t count = this->count;
    uint64_t sum = this->sum;
    uint64_t min = this->min;
    uint64_t max = this->max;

    uint64_t p50 = cum_search<50>(buckets, count);
    uint64_t p99 = cum_search<99>(buckets, count);

    return { buckets, count, sum, min, max, p50, p99 };
}

const std::string MetricsCollectorsBaseline::name()
{
    return "Baseline Metrics Collector";
}