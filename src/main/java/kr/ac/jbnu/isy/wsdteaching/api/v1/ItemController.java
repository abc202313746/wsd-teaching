package kr.ac.jbnu.isy.wsdteaching.api.v1;

import kr.ac.jbnu.isy.wsdteaching.api.dto.ItemDto;
import kr.ac.jbnu.isy.wsdteaching.api.request.ItemCreateRequest;
import kr.ac.jbnu.isy.wsdteaching.api.request.ItemPriceRequest;
import kr.ac.jbnu.isy.wsdteaching.api.request.ItemUpdateRequest;
import kr.ac.jbnu.isy.wsdteaching.api.response.ApiResponse;
import kr.ac.jbnu.isy.wsdteaching.api.response.DeleteResult;
import kr.ac.jbnu.isy.wsdteaching.api.response.ItemPage;
import kr.ac.jbnu.isy.wsdteaching.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public ApiResponse<ItemPage> getItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(itemService.list(keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ItemDto> getItem(@PathVariable long id) {
        return ApiResponse.success(itemService.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ItemDto>> createItem(@RequestBody ItemCreateRequest request) {
        ItemDto item = itemService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/items/" + item.getId()))
                .body(ApiResponse.success(item));
    }

    @PutMapping("/{id}")
    public ApiResponse<ItemDto> updateItem(
            @PathVariable long id,
            @RequestBody ItemUpdateRequest request
    ) {
        return ApiResponse.success(itemService.replace(id, request));
    }

    @PutMapping("/{id}/price")
    public ApiResponse<ItemDto> updatePrice(
            @PathVariable long id,
            @RequestBody ItemPriceRequest request
    ) {
        return ApiResponse.success(itemService.replacePrice(id, request.price()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResult> deleteItem(@PathVariable long id) {
        return ApiResponse.success(itemService.delete(id));
    }

    @DeleteMapping
    public ApiResponse<DeleteResult> deleteItems(@RequestParam List<Long> ids) {
        return ApiResponse.success(itemService.deleteBatch(ids));
    }
}
