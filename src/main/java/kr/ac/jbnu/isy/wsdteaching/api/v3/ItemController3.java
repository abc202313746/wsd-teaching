package kr.ac.jbnu.isy.wsdteaching.api.v3;

import kr.ac.jbnu.isy.wsdteaching.api.dto.ItemDto;
import kr.ac.jbnu.isy.wsdteaching.api.request.ItemCreateRequest;
import kr.ac.jbnu.isy.wsdteaching.api.response.ApiResponse;
import kr.ac.jbnu.isy.wsdteaching.api.response.HeaderItemResult;
import kr.ac.jbnu.isy.wsdteaching.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v3/items")
public class ItemController3 {
    private final ItemService itemService;

    public ItemController3(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping("/with-header")
    public ResponseEntity<ApiResponse<HeaderItemResult>> createItemWithHeader(
            @RequestBody ItemCreateRequest request,
            @RequestHeader(value = "X-USER-ID", required = false) String userId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        ItemDto item = itemService.create(request);
        HeaderItemResult result = new HeaderItemResult(
                item, userId, authorization != null && !authorization.isBlank()
        );
        return ResponseEntity.created(URI.create("/api/v1/items/" + item.getId()))
                .body(ApiResponse.success(result));
    }
}
