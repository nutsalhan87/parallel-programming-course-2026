#pragma once

#include "metrics.hpp"
#include <atomic>

constexpr std::size_t MUTEXES = 16;

class MetricsCollectorsSharded final : public MetricsCollector {
private:
    std::array<std::mutex, MUTEXES> mutexes = {};
    std::array<uint64_t, BUCKETS> buckets = {};
    std::atomic_uint64_t count = 0;
    std::atomic_uint64_t sum = 0;
    std::atomic_uint64_t min = UINT64_MAX;
    std::atomic_uint64_t max = 0;

public:
    void record(uint64_t value) override;
    Snapshot snapshot() override;
    const std::string name() override;
};