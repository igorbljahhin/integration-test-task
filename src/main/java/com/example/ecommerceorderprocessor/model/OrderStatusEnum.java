package com.example.ecommerceorderprocessor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum OrderStatusEnum {
    PENDING("pending"),
    UPDATED("updated"),
    CONFIRMED("confirmed"),
    PAID("paid"),
    SHIPPED("shipped"),
    CANCELLED("cancelled");

    private final static Map<String, OrderStatusEnum> ENUM_CODE_MAP;

    static {
        ENUM_CODE_MAP = Arrays.stream(OrderStatusEnum.values())
                .collect(Collectors.toMap(
                        OrderStatusEnum::getCode,
                        Function.identity()));
    }

    private final String code;

    OrderStatusEnum(String code) {
        this.code = code;
    }

    @JsonCreator
    public static OrderStatusEnum fromCode(String code) {
        return ENUM_CODE_MAP.get(code);
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return this.code;
    }
}
