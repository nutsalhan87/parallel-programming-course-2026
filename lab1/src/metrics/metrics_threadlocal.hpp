#pragma once

#include "metrics.hpp"
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

class MetricsCollectorsThreadLocal final : public MetricsCollector {
private:
    struct ThreadLocalCollectorState {
        std::array<std::atomic_uint64_t, BUCKETS> buckets = {};
        std::atomic_uint64_t count = 0;
        std::atomic_uint64_t sum = 0;
        std::atomic_uint64_t min = UINT64_MAX;
        std::atomic_uint64_t max = 0;
    };

    const uint64_t _id = next_collector_id();
    std::mutex _list_lock;
    std::vector<std::unique_ptr<ThreadLocalCollectorState>> _states;

    static std::uint64_t next_collector_id();
    ThreadLocalCollectorState* get_my_state();

public:
    void record(uint64_t value) override;
    Snapshot snapshot() override;
    const std::string name() override;
};