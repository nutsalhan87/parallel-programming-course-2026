#include "metrics.hpp"

#include <algorithm>
#include <cmath>
#include <iomanip>
#include <numeric>
#include <vector>

void draw_histogram(std::ostream& os, const std::span<const uint64_t> data_x,
    const std::span<const uint64_t> data_y,
    const std::string& title, const std::string& x_label,
    const std::string& y_label, bool use_log_scale, int width,
    int height)
{
    // Берем минимальный размер на случай несовпадения длин
    size_t n = std::min(std::size(data_x), std::size(data_y));
    if (n == 0)
        return;

    if (width <= 0)
        width = 80;
    if (height <= 0)
        height = 15;

    // 1. Маппинг данных на визуальные колонки (Downsampling / Upsampling)
    std::vector<double> cols(width, 0.0);
    if (n > static_cast<size_t>(width)) {
        // Если точек больше, чем ширина экрана — берем максимум в группе
        // (Downsampling)
        for (size_t i = 0; i < n; ++i) {
            size_t c = (i * width) / n;
            cols[c] = std::max(cols[c], static_cast<double>(data_y[i]));
        }
    } else {
        // Если точек меньше (например 5 точек на 80 символов) — растягиваем
        // столбцы (Upsampling)
        for (size_t c = 0; c < static_cast<size_t>(width); ++c) {
            size_t i = (c * n) / width;
            cols[c] = static_cast<double>(data_y[i]);
        }
    }

    // 2. Логарифмирование значений по оси Y при необходимости
    double max_val = 0.0;
    for (int i = 0; i < width; ++i) {
        if (use_log_scale && cols[i] > 0) {
            cols[i] = std::log10(cols[i] + 1.0);
        }
        if (cols[i] > max_val)
            max_val = cols[i];
    }

    // Заголовок
    if (!title.empty()) {
        os << "--- " << title << " ("
           << (use_log_scale ? "Log10 Scale" : "Linear Scale") << ") ---\n";
    }

    if (max_val <= 0.0) {
        os << "[График пуст]\n";
        return;
    }

    if (!y_label.empty()) {
        os << std::setw(8) << y_label << " ^\n";
    }

    static const char* BLOCKS[] = { " ", " ", "▂", "▃", "▄", "▅", "▆", "▇", "█" };

    // 3. Отрисовка столбцов
    for (int r = height; r > 0; --r) {
        if (r == height) {
            if (use_log_scale) {
                os << "10^" << std::left << std::setw(5) << std::fixed
                   << std::setprecision(1) << max_val << " | ";
            } else {
                if (max_val >= 100000000.0) {
                    // Если число не влезает в 8 символов, используем scientific
                    // notation (например: 1.0e+08)
                    os << std::right << std::setw(8) << std::scientific
                       << std::setprecision(1) << max_val << " | ";
                    os << std::defaultfloat; // Сбрасываем флаг форматирования
                                             // обратно
                } else {
                    os << std::right << std::setw(8)
                       << static_cast<uint64_t>(max_val) << " | ";
                }
            }
        } else if (r == 1) {
            os << std::right << std::setw(8) << 0 << " | ";
        } else {
            os << std::right << std::setw(8) << "" << " | ";
        }

        // Отрисовка самих блоков
        for (int c = 0; c < width; ++c) {
            double scaled_height = (cols[c] / max_val) * height;
            double cell_fill = scaled_height - (r - 1);

            if (cell_fill >= 1.0) {
                os << BLOCKS[8];
            } else if (cell_fill <= 0.0) {
                os << BLOCKS[0];
            } else {
                int block_idx = static_cast<int>(cell_fill * 8.0);
                os << BLOCKS[std::clamp(block_idx, 1, 8)];
            }
        }
        os << '\n';
    }

    // Линия оси X
    os << std::setw(8) << "" << " +";
    for (int c = 0; c < width; ++c)
        os << '-';
    if (!x_label.empty()) {
        os << " > " << x_label;
    }
    os << "\n";

    // 4. Отрисовка значений под осью X
    std::string x_labels_row(width, ' ');
    int last_end = -1; // Индекс конца последней нарисованной строки

    for (size_t i = 0; i < n; ++i) {
        std::ostringstream oss;
        oss << data_x[i];
        std::string label = oss.str();
        int len = static_cast<int>(label.length());

        int start_col = (i * width) / n;
        int end_col = ((i + 1) * width) / n;
        int center_col = start_col + (end_col - start_col) / 2;

        int pos = center_col - len / 2;
        if (pos < 0)
            pos = 0;

        // Если надпись вылезает за правый край, прижимаем её к краю
        if (pos + len > width) {
            pos = width - len;
        }

        // Проверяем, не наезжает ли надпись на предыдущую (оставляем минимум 1
        // пробел)
        if (last_end != -1 && pos <= last_end + 1) {
            if (i == n - 1) {
                // Для самого последнего элемента делаем исключение:
                // Затираем пробелами всё слово слева, с которым мы сливаемся
                int wipe_idx = pos - 1;
                while (wipe_idx >= 0 && x_labels_row[wipe_idx] != ' ') {
                    x_labels_row[wipe_idx] = ' ';
                    wipe_idx--;
                }
            } else {
                continue; // Пропускаем элемент, так как ему нет места
            }
        }

        // Записываем надпись в итоговую строку
        for (int j = 0; j < len; ++j) {
            x_labels_row[pos + j] = label[j];
        }
        last_end = pos + len;
    }

    // Выводим строку с подписями с правильным отступом слева
    os << std::setw(8) << "" << "  " << x_labels_row << "\n\n";
}

void print_snapshot(std::ostream& os, const Snapshot& snap, bool use_log_scale,
    int width, int height)
{
    os << "=== Snapshot Summary ===\n"
       << "Count: " << snap.count << " \t| Sum: " << snap.sum << "\n"
       << "Min:   " << snap.min << " \t| Max: " << snap.max << "\n"
       << "P50:   " << snap.p50 << " \t| P99: " << snap.p99 << "\n\n";

    std::vector<uint64_t> bucket_idx(BUCKETS);
    std::iota(bucket_idx.begin(), bucket_idx.end(), 1);

    draw_histogram(os, bucket_idx, snap.buckets, "Latency Distribution",
        "Buckets", "Count", use_log_scale, width, height);
}