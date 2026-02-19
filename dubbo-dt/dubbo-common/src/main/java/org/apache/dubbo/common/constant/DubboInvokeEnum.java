package org.apache.dubbo.common.constant;

import org.apache.dubbo.common.api.UserProto;

public enum DubboInvokeEnum  {
    AGENT_NAME_HELLO("你好"),
    AGENT_NAME_HELLO2("你好号2"),
    AGENT_OTHER("扩展测试"),
    AGENT_PROTO(UserProto.UserRequest.newBuilder()
            .setName("你好")
            .build());
    private Object value;

    DubboInvokeEnum(Object reqObj) {
        this.value = reqObj;
    }
    public Object getValue() {
        return value;
    }
}