package com.acme.api.dto;
import java.util.List;
public record UserDto(String id, int age, boolean active, Role role, List<String> tags) {}
