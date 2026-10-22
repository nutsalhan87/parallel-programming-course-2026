#pragma once

#include "metrics.hpp"
#include <atomic>
#include <memory>
#include <mutex>
#include <vector>

class MetricsCollectorsDB final : public MetricsCollector {
private:
    struct DBCollectorState {
        std::array<uint64_t, BUCKETS> buckets = {};
        uint64_t count = 0;
        uint64_t sum = 0;
        uint64_t min = UINT64_MAX;
        uint64_t max = 0;
        void clear() {
            buckets.fill(0);
            count = 0;
            sum = 0;
            min = UINT64_MAX;
            max = 0;
        }
    };

    struct alignas(64) ThreadBuffers {
        std::atomic_int8_t inside = -1;
        DBCollectorState state[2] = {};
    };
    
    const uint64_t _id = next_collector_id();
    std::mutex _list_lock;
    std::vector<std::unique_ptr<ThreadBuffers>> _buffers;
    std::atomic_int8_t active = 0;
    DBCollectorState global = {};
    bool check_active;

    static std::uint64_t next_collector_id();
    ThreadBuffers* get_my_buffers();

public:
    MetricsCollectorsDB(bool check_active = true);
    void record(uint64_t value) override;
    Snapshot snapshot() override;
    const std::string name() override;
};