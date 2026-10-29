package com.mockstudio.task;

import com.mockstudio.asset.Asset;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 所有状态迁移都是带前置状态条件的 UPDATE，返回受影响行数：
 * 0 表示迁移不合法（例如任务已处于终态），调用方据此放弃后续动作。
 */
@Mapper
public interface TaskMapper {

    String SELECT_TASK = "SELECT t.*, tp.title AS template_title FROM task t JOIN template tp ON tp.id = t.template_id ";

    // ---------- 写入 ----------

    @Insert("INSERT INTO task (user_id, template_id, status, sim_config, created_at, updated_at) "
            + "VALUES (#{userId}, #{templateId}, #{status}, #{simConfig}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Task task);

    @Insert("INSERT INTO task_asset (task_id, slot_index, asset_id) VALUES (#{taskId}, #{slotIndex}, #{assetId})")
    int insertAssetRef(@Param("taskId") long taskId, @Param("slotIndex") int slotIndex, @Param("assetId") long assetId);

    // ---------- 状态迁移 ----------

    @Update("UPDATE task SET status = 'RUNNING', started_at = #{now}, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markRunning(@Param("id") long id, @Param("now") Instant now);

    @Update("UPDATE task SET stage = #{stage}, updated_at = #{now} WHERE id = #{id} AND status = 'RUNNING'")
    int updateStage(@Param("id") long id, @Param("stage") TaskStage stage, @Param("now") Instant now);

    @Update("UPDATE task SET status = 'SUCCEEDED', result_path = #{resultPath}, result_url = #{resultUrl}, "
            + "finished_at = #{now}, updated_at = #{now} WHERE id = #{id} AND status = 'RUNNING'")
    int markSucceeded(@Param("id") long id, @Param("resultPath") String resultPath,
                      @Param("resultUrl") String resultUrl, @Param("now") Instant now);

    @Update("UPDATE task SET status = 'FAILED', stage = #{stage}, error_code = #{code}, error_message = #{message}, "
            + "finished_at = #{now}, updated_at = #{now} WHERE id = #{id} AND status IN ('PENDING', 'RUNNING')")
    int markFailed(@Param("id") long id, @Param("stage") TaskStage stage, @Param("code") String code,
                   @Param("message") String message, @Param("now") Instant now);

    @Update("UPDATE task SET status = 'FAILED', error_code = #{code}, error_message = #{message}, "
            + "finished_at = #{now}, updated_at = #{now} WHERE status IN ('PENDING', 'RUNNING')")
    int failAllUnfinished(@Param("code") String code, @Param("message") String message, @Param("now") Instant now);

    @Update("UPDATE task_step_log SET status = 'FAILED', message = #{message}, ended_at = #{now} "
            + "WHERE status = 'RUNNING'")
    int failAllRunningSteps(@Param("message") String message, @Param("now") Instant now);

    @Insert("INSERT INTO task_step_log (task_id, stage, status, started_at) "
            + "VALUES (#{taskId}, #{stage}, 'RUNNING', #{now})")
    int insertStepStarted(@Param("taskId") long taskId, @Param("stage") TaskStage stage, @Param("now") Instant now);

    @Update("UPDATE task_step_log SET status = #{status}, message = #{message}, ended_at = #{now} "
            + "WHERE task_id = #{taskId} AND stage = #{stage} AND status = 'RUNNING'")
    int finishStep(@Param("taskId") long taskId, @Param("stage") TaskStage stage, @Param("status") String status,
                   @Param("message") String message, @Param("now") Instant now);

    // ---------- 查询（只读，不推进任务） ----------

    @Select(SELECT_TASK + "WHERE t.id = #{id}")
    Task findById(@Param("id") long id);

    @Select(SELECT_TASK + "WHERE t.id = #{id} AND t.user_id = #{userId}")
    Task findByIdForUser(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT t.*, tp.title AS template_title FROM task t JOIN template tp ON tp.id = t.template_id
            WHERE t.user_id = #{userId}
            <if test="templateId != null and templateId != ''">AND t.template_id = #{templateId}</if>
            <if test="beforeId != null">AND t.id &lt; #{beforeId}</if>
            ORDER BY t.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Task> list(@Param("userId") long userId, @Param("templateId") String templateId,
                    @Param("beforeId") Long beforeId, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT t.*, tp.title AS template_title FROM task t JOIN template tp ON tp.id = t.template_id
            WHERE t.user_id = #{userId} AND t.id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY t.id DESC
            </script>
            """)
    List<Task> findByIdsForUser(@Param("userId") long userId, @Param("ids") Collection<Long> ids);

    @Select("""
            <script>
            SELECT * FROM task_asset WHERE task_id IN
            <foreach item="id" collection="taskIds" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY task_id, slot_index
            </script>
            """)
    List<TaskAssetRef> findAssetRefs(@Param("taskIds") Collection<Long> taskIds);

    @Select("""
            <script>
            SELECT * FROM task_step_log WHERE task_id IN
            <foreach item="id" collection="taskIds" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY task_id, id
            </script>
            """)
    List<TaskStepLog> findStepLogs(@Param("taskIds") Collection<Long> taskIds);

    @Select("SELECT a.* FROM task_asset ta JOIN asset a ON a.id = ta.asset_id "
            + "WHERE ta.task_id = #{taskId} ORDER BY ta.slot_index")
    List<Asset> findAssetsOfTask(@Param("taskId") long taskId);
}
