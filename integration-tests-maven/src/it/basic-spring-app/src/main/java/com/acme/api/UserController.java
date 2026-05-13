package com.acme.api;

import com.acme.api.dto.UserDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    @GetMapping("/{id}")
    public UserDto getUser(@PathVariable String id) { return null; }

    @PostMapping
    public UserDto createUser(@RequestBody UserDto body) { return body; }

    @GetMapping("/page")
    public com.acme.api.dto.Page<UserDto> page() { throw new UnsupportedOperationException(); }
}
