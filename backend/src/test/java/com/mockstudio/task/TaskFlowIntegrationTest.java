package com.mockstudio.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockstudio.common.Times;
import com.mockstudio.simulation.SimulationConfig;
import com.mockstudio.simulation.SimulationSettings;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端（HTTP → Service → 后台线程池 → H2）验证核心行为。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskFlowIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    SimulationSettings simulation;
    @Autowired
    TaskMapper taskMapper;
    @Autowired
    TaskRecovery recovery;
    @Autowired
    Clock clock;

    @AfterEach
    void resetSimulation() {
        simulation.update(SimulationConfig.instant());
    }

    // ---------------------------------------------------------------- 成功路径

    @Test
    void submitReturnsImmediatelyAndBackgroundRunsAllStagesInOrder() throws Exception {
        Cookie alice = login("alice@demo.local");
        long assetId = upload(alice, "pet-portrait", png(Color.ORANGE));

        JsonNode created = createTask(alice, "pet-portrait", List.of(assetId));
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        long taskId = created.get("id").asLong();

        JsonNode done = awaitTerminal(alice, taskId);
        assertThat(done.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(done.get("resultUrl").asText()).isEqualTo("/api/tasks/" + taskId + "/result");
        assertThat(stagesWithStatus(done)).containsExactly("ASSET_CHECK:DONE", "GENERATE:DONE", "FINALIZE:DONE");

        mvc.perform(get("/api/tasks/" + taskId + "/result").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    // ---------------------------------------------------------------- 失败中断

    @Test
    void failureAtGenerateStopsFinalizeAndKeepsReadableReason() throws Exception {
        simulation.update(new SimulationConfig(Map.of(), TaskStage.GENERATE));
        Cookie alice = login("alice@demo.local");
        long a1 = upload(alice, "demo-postcard", png(Color.BLUE));
        long a2 = upload(alice, "demo-postcard", jpg(Color.GREEN));

        long taskId = createTask(alice, "demo-postcard", List.of(a1, a2)).get("id").asLong();
        JsonNode done = awaitTerminal(alice, taskId);

        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("stage").asText()).isEqualTo("GENERATE");
        assertThat(done.get("errorCode").asText()).isEqualTo("SIMULATED_FAILURE");
        assertThat(done.get("errorMessage").asText()).contains("模拟生成");
        assertThat(done.get("resultUrl").isNull()).isTrue();
        // FINALIZE 从未开始
        assertThat(stagesWithStatus(done)).containsExactly("ASSET_CHECK:DONE", "GENERATE:FAILED");

        mvc.perform(get("/api/tasks/" + taskId + "/result").cookie(alice)).andExpect(status().isNotFound());
    }

    @Test
    void simulationConfigIsSnapshottedAtCreation() throws Exception {
        Cookie alice = login("alice@demo.local");
        long assetId = upload(alice, "art-poster", png(Color.RED));
        // 创建时配置为 FINALIZE 失败
        simulation.update(new SimulationConfig(Map.of(TaskStage.ASSET_CHECK, 300L), TaskStage.FINALIZE));
        long taskId = createTask(alice, "art-poster", List.of(assetId)).get("id").asLong();
        // 创建后立即恢复配置，不应影响已受理的任务
        simulation.update(SimulationConfig.instant());

        JsonNode done = awaitTerminal(alice, taskId);
        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("stage").asText()).isEqualTo("FINALIZE");
    }

    // ---------------------------------------------------------------- 状态只读/不回退

    @Test
    void queryingDoesNotAdvanceTask() throws Exception {
        Cookie alice = login("alice@demo.local");
        // 直接插入一个 PENDING 任务且不投递到线程池：只有后台执行器才能推进它
        Task t = new Task();
        t.setUserId(meId(alice));
        t.setTemplateId("art-poster");
        t.setStatus(TaskStatus.PENDING);
        t.setCreatedAt(Times.now(clock));
        t.setUpdatedAt(Times.now(clock));
        taskMapper.insert(t);

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/tasks/" + t.getId()).cookie(alice))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.steps.length()").value(0));
            mvc.perform(get("/api/tasks").param("ids", String.valueOf(t.getId())).cookie(alice))
                    .andExpect(jsonPath("$.items[0].status").value("PENDING"));
        }
        // 清理：标记失败以免影响其它测试
        taskMapper.markFailed(t.getId(), null, "TEST", "test", Times.now(clock));
    }

    @Test
    void terminalTaskCannotTransitionBack() throws Exception {
        Cookie alice = login("alice@demo.local");
        long assetId = upload(alice, "art-poster", png(Color.PINK));
        long taskId = createTask(alice, "art-poster", List.of(assetId)).get("id").asLong();
        assertThat(awaitTerminal(alice, taskId).get("status").asText()).isEqualTo("SUCCEEDED");

        assertThat(taskMapper.markRunning(taskId, Times.now(clock))).isZero();
        assertThat(taskMapper.updateStage(taskId, TaskStage.ASSET_CHECK, Times.now(clock))).isZero();
        assertThat(taskMapper.markFailed(taskId, TaskStage.GENERATE, "X", "x", Times.now(clock))).isZero();

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/tasks/" + taskId).cookie(alice))
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        }
    }

    @Test
    void restartRecoveryFailsUnfinishedTasks() throws Exception {
        Cookie alice = login("alice@demo.local");
        Task t = new Task();
        t.setUserId(meId(alice));
        t.setTemplateId("art-poster");
        t.setStatus(TaskStatus.PENDING);
        t.setCreatedAt(Times.now(clock));
        t.setUpdatedAt(Times.now(clock));
        taskMapper.insert(t);
        taskMapper.markRunning(t.getId(), Times.now(clock));

        recovery.afterSingletonsInstantiated();

        mvc.perform(get("/api/tasks/" + t.getId()).cookie(alice))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value(TaskRecovery.CODE));
    }

    // ---------------------------------------------------------------- 校验

    @Test
    void serverValidatesUploadsAndSubmission() throws Exception {
        Cookie alice = login("alice@demo.local");

        // 类型：GIF 被拒绝（按文件头识别，而非客户端声明）
        mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file", "x.png", "image/png",
                        "GIF89a........".getBytes())).param("templateId", "art-poster").cookie(alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MIME"));

        // 类型：宠物贴纸仅接受 PNG
        mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpg(Color.CYAN)))
                        .param("templateId", "pet-sticker").cookie(alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MIME"));

        // 大小：宠物贴纸限制 2MB
        byte[] big = new byte[2 * 1024 * 1024 + 10];
        System.arraycopy(png(Color.BLACK), 0, big, 0, 16);
        mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file", "big.png", "image/png", big))
                        .param("templateId", "pet-sticker").cookie(alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        // 数量：明信片需要 2 张
        long one = upload(alice, "demo-postcard", png(Color.WHITE));
        mvc.perform(post("/api/tasks").cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("templateId", "demo-postcard", "assetIds", List.of(one)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_COUNT_MISMATCH"));

        // 重复图片
        mvc.perform(post("/api/tasks").cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("templateId", "demo-postcard", "assetIds", List.of(one, one)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ASSET"));

        // 不存在的模板
        mvc.perform(post("/api/tasks").cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("templateId", "nope", "assetIds", List.of(one)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
        mvc.perform(get("/api/templates/nope")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- 身份与隔离

    @Test
    void usersCannotSeeOrUseEachOthersData() throws Exception {
        Cookie alice = login("alice@demo.local");
        Cookie bob = login("bob@demo.local");
        long aliceAsset = upload(alice, "art-poster", png(Color.YELLOW));
        long aliceTask = createTask(alice, "art-poster", List.of(aliceAsset)).get("id").asLong();

        mvc.perform(get("/api/tasks/" + aliceTask).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").param("ids", String.valueOf(aliceTask)).cookie(bob))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/assets/" + aliceAsset + "/content").cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(post("/api/tasks").cookie(bob).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("templateId", "art-poster", "assetIds", List.of(aliceAsset)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));

        JsonNode bobHistory = json.readTree(mvc.perform(get("/api/tasks").cookie(bob))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode item : bobHistory.get("items")) {
            assertThat(item.get("id").asLong()).isNotEqualTo(aliceTask);
        }
    }

    @Test
    void protectedEndpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/templates")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@demo.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void templatesCanBeFilteredByCategory() throws Exception {
        mvc.perform(get("/api/templates").param("category", "pets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoryId").value("pets"));
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$.length()").value(4));
    }

    // ---------------------------------------------------------------- helpers

    private Cookie login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "demo1234"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie c = r.getResponse().getCookie("ms_session");
        assertThat(c).isNotNull();
        return new Cookie(c.getName(), c.getValue());
    }

    private long meId(Cookie cookie) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/me").cookie(cookie)).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
    }

    private long upload(Cookie cookie, String templateId, byte[] bytes) throws Exception {
        String body = mvc.perform(multipart("/api/assets")
                        .file(new MockMultipartFile("file", "img", "application/octet-stream", bytes))
                        .param("templateId", templateId).cookie(cookie))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("id").asLong();
    }

    private JsonNode createTask(Cookie cookie, String templateId, List<Long> assetIds) throws Exception {
        String body = mvc.perform(post("/api/tasks").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("templateId", templateId, "assetIds", assetIds))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private JsonNode awaitTerminal(Cookie cookie, long taskId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode t = json.readTree(mvc.perform(get("/api/tasks/" + taskId).cookie(cookie))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            String s = t.get("status").asText();
            if (s.equals("SUCCEEDED") || s.equals("FAILED")) {
                return t;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Task " + taskId + " did not finish in time");
    }

    private static List<String> stagesWithStatus(JsonNode task) {
        List<String> out = new ArrayList<>();
        for (JsonNode s : task.get("steps")) {
            out.add(s.get("stage").asText() + ":" + s.get("status").asText());
        }
        return out;
    }

    private static byte[] png(Color color) throws Exception {
        return encode(color, "png");
    }

    private static byte[] jpg(Color color) throws Exception {
        return encode(color, "jpg");
    }

    private static byte[] encode(Color color, String format) throws Exception {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }
}
