#pragma once

#include "metrics.hpp"
#include <mutex>

class MetricsCollectorsStub final : public MetricsCollector {
private:
    std::mutex mutex = std::mutex();

public:
    void record(uint64_t value) override;
    Snapshot snapshot() override;
    const std::string name() override;
};