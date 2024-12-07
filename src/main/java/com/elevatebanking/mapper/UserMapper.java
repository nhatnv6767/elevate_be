package com.elevatebanking.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.elevatebanking.dto.auth.AuthDTOs.AuthRequest;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
import com.elevatebanking.entity.user.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    User authRequestToUser(AuthRequest authRequest);

    @Mapping(target = "roles", expression = "java(user.getRoles().stream().map(Role::getName).toArray(String[]::new))")
    AuthResponse userToAuthResponse(User user);

}
