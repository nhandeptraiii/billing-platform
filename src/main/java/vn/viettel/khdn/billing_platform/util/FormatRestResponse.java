package vn.viettel.khdn.billing_platform.util;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletResponse;
import vn.viettel.khdn.billing_platform.model.RestResponse;

/**
 * Tự động wrap tất cả response thành RestResponse<T>.
 * Bỏ qua: byte[], Resource, stream (Excel download), RestResponse đã wrap.
 */
@ControllerAdvice
public class FormatRestResponse implements ResponseBodyAdvice {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        Class<?> paramType = returnType.getParameterType();
        return !(byte[].class.equals(paramType)
                || org.springframework.core.io.Resource.class.isAssignableFrom(paramType));
    }

    @Override
    @Nullable
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
        int status = servletResponse.getStatus();

        if (body instanceof String) return body;
        if (body instanceof byte[]) return body;
        if (body instanceof org.springframework.core.io.Resource) return body;

        if (selectedContentType != null
                && (selectedContentType.includes(MediaType.APPLICATION_OCTET_STREAM)
                || selectedContentType.includes(MediaType.APPLICATION_PDF))) {
            return body;
        }

        if (status >= 400) return body;

        if (body instanceof RestResponse<?> restBody) {
            if (restBody.getMessage() == null) restBody.setMessage("Call API successful");
            return body;
        }

        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(status);
        res.setMessage("Call API successful");
        res.setData(body);
        return res;
    }
}
