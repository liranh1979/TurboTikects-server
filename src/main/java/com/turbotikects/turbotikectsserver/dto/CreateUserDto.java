package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserDto {
    private String username;
    private String displayName;
    private String password;
    private String email;
}