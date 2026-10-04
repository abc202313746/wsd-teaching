package kr.ac.jbnu.isy.wsdteaching.api.response;

public record ApiResponse<T>(String status, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data);
    }

    public static ApiResponse<ApiError> error(String code, String message) {
        return new ApiResponse<>("error", new ApiError(code, message));
    }
}
