package com.acme.api.dto;

import java.util.List;

public record Page<T>(List<T> content, long totalElements, int number) {}
