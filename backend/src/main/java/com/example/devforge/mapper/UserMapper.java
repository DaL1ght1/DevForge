package com.example.devforge.mapper;

import com.example.devforge.dto.UserResponseDto;
import com.example.devforge.entity.User;
import org.mapstruct.*;

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
  UserResponseDto toResponseDto(User user);
}
