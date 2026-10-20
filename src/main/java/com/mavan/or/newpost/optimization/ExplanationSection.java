package com.mavan.or.newpost.optimization;

import java.util.List;

/** One titled step of the human-readable solution explanation. */
public record ExplanationSection(String title, List<String> lines) {
    public ExplanationSection {
        lines = List.copyOf(lines);
    }
}
