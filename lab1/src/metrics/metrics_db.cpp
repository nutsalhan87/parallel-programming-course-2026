#include "metrics_db.hpp"
#include "metrics.hpp"
#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <thread>

uint64_t MetricsCollectorsDB::next_collector_id() {
    static std::atomic_uint64_t counter = 1;
    return counter.fetch_add(1);
}

MetricsCollectorsDB::ThreadBuffers* MetricsCollectorsDB::get_my_buffers() {
    struct TLSSlot { uint64_t id = 0; ThreadBuffers* state = nullptr; };
    static thread_local TLSSlot slot;
    if (slot.id != _id) {
        auto s = std::make_unique<ThreadBuffers>();
        ThreadBuffers* raw = s.get();
        {
            auto g = std::lock_guard<std::mutex>(_list_lock);
            _buffers.push_back(std::move(s));
        }
        slot.id = _id;
        slot.state = raw;
    }
    return slot.state;
}

MetricsCollectorsDB::MetricsCollectorsDB(bool check_active) : check_active(check_active) {}

void MetricsCollectorsDB::record(uint64_t value)
{
    ThreadBuffers* buffers = get_my_buffers();
    int8_t active;
    while (true) {
        active = this->active.load(std::memory_order_seq_cst);
        buffers->inside.store(active, std::memory_order_seq_cst);
        if (!check_active || active == this->active.load(std::memory_order_seq_cst)) {
            break;
        }
        buffers->inside.store(-1, std::memory_order_seq_cst);
    }

    auto idx = std::max(std::min(value / BUCKET_STEP, BUCKETS - 1ul), 0ul);
    buffers->state[active].buckets[idx]++;
    buffers->state[active].count++;
    buffers->state[active].sum += value;
    buffers->state[active].min = std::min(buffers->state[active].min, value);
    buffers->state[active].max = std::max(buffers->state[active].max, value);

    buffers->inside.store(-1, std::memory_order_release);
}

Snapshot MetricsCollectorsDB::snapshot()
{
    _list_lock.lock();

    int8_t old = this->active.load(std::memory_order_relaxed);
    while (!this->active.compare_exchange_weak(old, 1 - old,
        std::memory_order_release)) {}

    for (auto &buffers : _buffers) {
        while (buffers->inside.load(std::memory_order_seq_cst) == old) {
            std::this_thread::yield();
        }

        for (std::size_t i = 0; i < std::size(global.buckets); ++i) {
            global.buckets[i] += buffers->state[old].buckets[i];
        }
        global.count += buffers->state[old].count;
        global.sum += buffers->state[old].sum;
        global.min = std::min(global.min, buffers->state[old].min);
        global.max = std::max(global.max, buffers->state[old].max);

        buffers->state[old].clear();
    }

    _list_lock.unlock();

    auto p50 = cum_search<50>(global.buckets, global.count);
    auto p99 = cum_search<99>(global.buckets, global.count);

    return {global.buckets, global.count, global.sum, global.min, global.max, p50, p99};
}

const std::string MetricsCollectorsDB::name()
{
    if (check_active) {
        return "Double Buffers Metrics Collector";
    } else {
        return "Double Buffers Metrics Collector (without checking active)";
    }
}