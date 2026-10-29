package com.mockstudio.asset;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AssetMapper {

    @Insert("INSERT INTO asset (user_id, storage_path, original_filename, mime_type, size_bytes, sha256, created_at) "
            + "VALUES (#{userId}, #{storagePath}, #{originalFilename}, #{mimeType}, #{sizeBytes}, #{sha256}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Asset asset);

    @Select("SELECT * FROM asset WHERE id = #{id} AND user_id = #{userId}")
    Asset findByIdForUser(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT * FROM asset WHERE user_id = #{userId} AND id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Asset> findByIdsForUser(@Param("userId") long userId, @Param("ids") Collection<Long> ids);
}
