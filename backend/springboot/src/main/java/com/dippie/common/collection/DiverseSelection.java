package com.dippie.common.collection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Hai hàm chọn danh sách gợi ý sao cho đa dạng theo nhóm (ví dụ thể loại hoặc hãng phim), viết generic để tái sử dụng cho nhiều kiểu dữ liệu.
public final class DiverseSelection {

    private DiverseSelection() {
    }

    // Lấy xoay vòng mỗi nhóm một phần tử cho đến khi đủ limit, tránh một nhóm áp đảo hết danh sách.
    public static <T> List<T> roundRobin(List<T> ranked, Function<T, String> groupOf, int limit) {
        Map<String, ArrayDeque<T>> groups = new LinkedHashMap<>();
        ranked.forEach(item -> groups.computeIfAbsent(groupOf.apply(item), key -> new ArrayDeque<>()).addLast(item));
        List<T> selected = new ArrayList<>();
        while (!groups.isEmpty() && selected.size() < limit) {
            var queues = groups.values().iterator();
            while (queues.hasNext() && selected.size() < limit) {
                var group = queues.next();
                selected.add(group.removeFirst());
                if (group.isEmpty()) {
                    queues.remove();
                }
            }
        }
        return List.copyOf(selected);
    }

    // Giữ nguyên thứ tự xếp hạng ban đầu nhưng mỗi nhóm chỉ lấy tối đa maxPerGroup phần tử.
    public static <T> List<T> capped(List<T> ranked, Function<T, String> groupOf, int limit, int maxPerGroup) {
        Map<String, Integer> counts = new HashMap<>();
        List<T> selected = new ArrayList<>(Math.min(limit, ranked.size()));
        for (T item : ranked) {
            if (counts.merge(groupOf.apply(item), 1, Integer::sum) <= maxPerGroup) {
                selected.add(item);
                if (selected.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }
}
