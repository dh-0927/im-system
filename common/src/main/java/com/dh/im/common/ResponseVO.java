package com.dh.im.common;

import com.dh.im.common.exception.ApplicationExceptionEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseVO<T> {
    private int code;
    private String msg;
    private T data;

    public ResponseVO(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static <T> ResponseVO<T>  successResponse(T data) {
        return new ResponseVO<T>(BaseErrorCode.SUCCESS.getCode(), BaseErrorCode.SUCCESS.getError(), data);
    }

    public static <T> ResponseVO<T> successResponse() {
        return new ResponseVO<T>(BaseErrorCode.SUCCESS.getCode(), BaseErrorCode.SUCCESS.getError());
    }

    public static <T> ResponseVO <T> errorResponse() {
        return new ResponseVO<T>(BaseErrorCode.SYSTEM_ERROR.getCode(), BaseErrorCode.SYSTEM_ERROR.getError());
    }

    public static <T> ResponseVO <T> errorResponse(int code, String msg) {
        return new ResponseVO<T>(code, msg);
    }

    public static <T> ResponseVO <T> errorResponse(ApplicationExceptionEnum exceptionEnum) {
        return new ResponseVO<T>(exceptionEnum.getCode(), exceptionEnum.getError());
    }

    public boolean isOk() {
        return this.code == 200;
    }

    public ResponseVO<T> success(){
        this.code = 200;
        this.msg = "success";
        return this;
    }

    public ResponseVO<T> success(T data){
        this.code = 200;
        this.msg = "success";
        this.data = data;
        return this;
    }




}
