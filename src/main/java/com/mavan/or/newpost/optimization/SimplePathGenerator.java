package com.mavan.or.newpost.optimization;

import java.util.*;

final class SimplePathGenerator {
    private final int n;

    SimplePathGenerator(int n) {
        if (n <= 1) throw new IllegalArgumentException("At least two locations are required");
        this.n = n;
    }

    List<List<Integer>> generate(int source, int destination) {
        if (source == destination) throw new IllegalArgumentException("Source and destination must differ");
        List<List<Integer>> result = new ArrayList<>();
        boolean[] visited = new boolean[n];
        List<Integer> path = new ArrayList<>();
        visited[source] = true;
        path.add(source);
        dfs(source, destination, visited, path, result);
        return result;
    }

    private void dfs(int current, int destination, boolean[] visited,
                     List<Integer> path, List<List<Integer>> result) {
        if (current == destination) {
            result.add(List.copyOf(path));
            return;
        }
        for (int next = 0; next < n; next++) {
            if (visited[next]) continue;
            visited[next] = true;
            path.add(next);
            dfs(next, destination, visited, path, result);
            path.remove(path.size() - 1);
            visited[next] = false;
        }
    }
}
