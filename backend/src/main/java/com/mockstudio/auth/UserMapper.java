package com.mockstudio.auth;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM app_user WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    @Insert("INSERT INTO app_user (email, password_hash, display_name, created_at) "
            + "VALUES (#{email}, #{passwordHash}, #{displayName}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(User user);

    @Insert("INSERT INTO user_session (token_hash, user_id, created_at, expires_at) "
            + "VALUES (#{tokenHash}, #{userId}, #{createdAt}, #{expiresAt})")
    int insertSession(@Param("tokenHash") String tokenHash, @Param("userId") long userId,
                      @Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt);

    @Select("SELECT u.* FROM user_session s JOIN app_user u ON u.id = s.user_id "
            + "WHERE s.token_hash = #{tokenHash} AND s.expires_at > #{now}")
    User findBySession(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Delete("DELETE FROM user_session WHERE token_hash = #{tokenHash}")
    int deleteSession(@Param("tokenHash") String tokenHash);
}
