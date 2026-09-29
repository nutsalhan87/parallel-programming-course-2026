#pragma once

#include <array>
#include <cstdint>
#include <ostream>
#include <span>

constexpr int BUCKET_STEP = 4;
constexpr int BUCKETS = 256;

struct Snapshot {
    std::array<uint64_t, BUCKETS> buckets;
    uint64_t count;
    uint64_t sum;
    uint64_t min;
    uint64_t max;
    uint64_t p50;
    uint64_t p99;
};

class MetricsCollector {
public:
    virtual ~MetricsCollector() = default;
    virtual void record(uint64_t value) = 0;
    virtual Snapshot snapshot() = 0;
    virtual const std::string name() = 0;
};

template <int Value>
    requires(0 < Value && Value <= 100)
uint64_t cum_search(std::array<uint64_t, BUCKETS>& buckets, uint64_t count)
{
    uint64_t lim = count * Value / 100;
    uint64_t accumulated = 0;
    for (std::size_t i = 0; i < BUCKETS; ++i) {
        accumulated += buckets[i];
        if (accumulated >= lim) {
            return i * BUCKET_STEP;
        }
    }
    return BUCKETS * BUCKET_STEP;
}

void draw_histogram(
    std::ostream& os,
    const std::span<const uint64_t> data_x,
    const std::span<const uint64_t> data_y,
    const std::string& title,
    const std::string& x_label,
    const std::string& y_label,
    bool use_log_scale = false,
    int width = 80,
    int height = 15);

void print_snapshot(std::ostream& os, const Snapshot& snap, bool use_log_scale = true, int width = 256, int height = 20);