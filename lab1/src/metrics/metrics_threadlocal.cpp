#include "metrics_threadlocal.hpp"
#include "metrics.hpp"
#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>

MetricsCollectorsThreadLocal::ThreadLocalCollectorState* MetricsCollectorsThreadLocal::get_my_state() {
        struct TLSSlot { uint64_t id = 0; ThreadLocalCollectorState* state = nullptr; };
        static thread_local TLSSlot slot;
        if (slot.id != _id) {
            auto s = std::make_unique<ThreadLocalCollectorState>();
            ThreadLocalCollectorState* raw = s.get();
            {
                auto g = std::lock_guard<std::mutex>(_list_lock);
                _states.push_back(std::move(s));
            }
            slot.id = _id;
            slot.state = raw;
        }
        return slot.state;
    }

void MetricsCollectorsThreadLocal::record(uint64_t value)
{
    ThreadLocalCollectorState* state = get_my_state();
    auto idx = std::max(std::min(value / BUCKET_STEP, BUCKETS - 1ul), 0ul);
    state->buckets[idx].fetch_add(1, std::memory_order_relaxed);
    state->count.fetch_add(1, std::memory_order_relaxed);
    state->sum.fetch_add(value, std::memory_order_relaxed);
    state->min.store(std::min(state->min.load(std::memory_order_acquire), value), std::memory_order_release);
    state->max.store(std::max(state->max.load(std::memory_order_acquire), value), std::memory_order_release);
}

Snapshot MetricsCollectorsThreadLocal::snapshot()
{
    std::array<uint64_t, BUCKETS> buckets = {};
    Snapshot snapshot = { buckets, 0, 0, UINT64_MAX, 0, 0, 0};

    _list_lock.lock();
    for (auto &state : _states) {
        for (std::size_t i = 0; i < std::size(buckets); ++i) {
            snapshot.buckets[i] += state->buckets[i].load(std::memory_order_relaxed);
        }
        snapshot.count += state->count.load(std::memory_order_relaxed);
        snapshot.sum += state->sum.load(std::memory_order_relaxed);
        snapshot.min = std::min(snapshot.min, state->min.load(std::memory_order_relaxed));
        snapshot.max = std::max(snapshot.max, state->max.load(std::memory_order_relaxed));
    }
    _list_lock.unlock();

    snapshot.p50 = cum_search<50>(snapshot.buckets, snapshot.count);
    snapshot.p99 = cum_search<99>(snapshot.buckets, snapshot.count);

    return snapshot;
}

const std::string MetricsCollectorsThreadLocal::name()
{
    return "ThreadLocal Metrics Collector";
}