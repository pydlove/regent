package com.pydlove.regent.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    int countByUsername(@Param("username") String username);

    int countByEmail(@Param("email") String email);

    UserData findByUsername(@Param("username") String username);

    UserData findByEmail(@Param("email") String email);

    int insert(UserData data);
}
