package com.example.devforge.mapper;

import com.example.devforge.dto.UserCreationDto;
import com.example.devforge.dto.UserResponseDto;
import com.example.devforge.entity.User;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    User toEntity(UserCreationDto userCreationDto);

    UserCreationDto toDto(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    User partialUpdate(UserCreationDto userCreationDto, @MappingTarget User user);

    User toEntity(UserResponseDto userDto);

    UserResponseDto toResponseDto(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    User partialUpdate(UserResponseDto userDto, @MappingTarget User user);
}