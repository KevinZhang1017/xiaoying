package com.mockstudio.template;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TemplateMapper {

    @Select("SELECT * FROM category ORDER BY sort_order, id")
    List<Category> findCategories();

    @Select("""
            <script>
            SELECT t.*, c.name AS category_name
            FROM template t JOIN category c ON c.id = t.category_id
            <where>
              <if test="categoryId != null and categoryId != ''">t.category_id = #{categoryId}</if>
            </where>
            ORDER BY t.sort_order, t.id
            </script>
            """)
    List<Template> findAll(@Param("categoryId") String categoryId);

    @Select("SELECT t.*, c.name AS category_name FROM template t JOIN category c ON c.id = t.category_id "
            + "WHERE t.id = #{id}")
    Template findById(@Param("id") String id);
}
