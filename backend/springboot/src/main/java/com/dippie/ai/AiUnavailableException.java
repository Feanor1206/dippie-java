package com.dippie.ai;

import com.dippie.common.error.ServiceUnavailableException;

public class AiUnavailableException extends ServiceUnavailableException {

    public static final String DEFAULT_MESSAGE = "Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau.";

    public AiUnavailableException() {
        super(DEFAULT_MESSAGE);
    }

    public AiUnavailableException(Throwable cause) {
        super(DEFAULT_MESSAGE);
        initCause(cause);
    }
}
