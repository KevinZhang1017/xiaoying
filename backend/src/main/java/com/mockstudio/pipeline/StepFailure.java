package com.mockstudio.pipeline;

/** 步骤内的预期失败：携带稳定错误码与面向用户的可读原因。 */
public class StepFailure extends RuntimeException {

    private final String code;

    public StepFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
