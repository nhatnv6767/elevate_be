package com.elevatebanking.mapper;

import com.elevatebanking.entity.user.Role;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import com.elevatebanking.dto.auth.UserUpdateRequest;
import com.elevatebanking.dto.auth.AuthDTOs.AuthRequest;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
import com.elevatebanking.entity.user.User;

import java.time.LocalDate;
import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "userId", source = "id") // Map id sang userId
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "roles", source = "roles", qualifiedByName = "roleListToStringArray")
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "expiresIn", ignore = true)
    AuthResponse userToAuthResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    // @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "dateOfBirth", source = "dateOfBirth", dateFormat = "yyyy-MM-dd")
    User authRequestToUser(AuthRequest authRequest);

    @Named("stringToLocalDate")
    default LocalDate stringToLocalDate(String date) {
        if (date == null)
            return null;
        return LocalDate.parse(date);
    }

    @Named("roleListToStringArray")
    default String[] roleListToStringArray(List<Role> roles) {
        if (roles == null)
            return new String[0];
        return roles.stream()
                .map(Role::getName)
                .toArray(String[]::new);
    }

    // User updateUserFromAuthRequest(AuthRequest request, @MappingTarget User
    // user);
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateUserFromUpdateRequest(UserUpdateRequest updateRequest, @MappingTarget User user);

    List<AuthResponse> usersToAuthResponses(List<User> users);

}
