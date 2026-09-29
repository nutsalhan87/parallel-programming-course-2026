#pragma once

#include "metrics.hpp"

class MetricsCollectorsBaseline final : public MetricsCollector {
private:
    std::array<uint64_t, BUCKETS> buckets = {};
    uint64_t count = 0;
    uint64_t sum = 0;
    uint64_t min = UINT64_MAX;
    uint64_t max = 0;

public:
    void record(uint64_t value) override;
    Snapshot snapshot() override;
    const std::string name() override;
};