package kr.ac.jbnu.isy.wsdteaching.service;

import kr.ac.jbnu.isy.wsdteaching.api.dto.ItemDto;
import kr.ac.jbnu.isy.wsdteaching.api.error.ApiException;
import kr.ac.jbnu.isy.wsdteaching.api.request.ItemCreateRequest;
import kr.ac.jbnu.isy.wsdteaching.api.request.ItemUpdateRequest;
import kr.ac.jbnu.isy.wsdteaching.api.response.DeleteResult;
import kr.ac.jbnu.isy.wsdteaching.api.response.ItemPage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ItemService {
    private final Map<Long, ItemDto> store = new LinkedHashMap<>();
    private long sequence = 1L;

    public synchronized ItemPage list(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("page는 0 이상, size는 1부터 100 사이여야 합니다.");
        }
        List<ItemDto> matches = store.values().stream()
                .filter(item -> keyword == null || item.getName().contains(keyword)).toList();
        List<ItemDto> items = matches.stream().skip((long) page * size).limit(size)
                .map(this::copy).toList();
        return new ItemPage(items, matches.size(), page, size);
    }

    public synchronized ItemDto get(long id) {
        return copy(find(id));
    }

    public synchronized ItemDto create(ItemCreateRequest request) {
        validateNameAndPrice(request.getName(), request.getPrice());
        ItemDto item = new ItemDto();
        item.setId(sequence++);
        item.setName(request.getName().trim());
        item.setPrice(request.getPrice());
        store.put(item.getId(), item);
        return copy(item);
    }

    public synchronized ItemDto replace(long id, ItemUpdateRequest request) {
        validateNameAndPrice(request.getName(), request.getPrice());
        ItemDto item = find(id);
        item.setName(request.getName().trim());
        item.setPrice(request.getPrice());
        return copy(item);
    }

    public synchronized ItemDto replacePrice(long id, Integer price) {
        validatePrice(price);
        ItemDto item = find(id);
        item.setPrice(price);
        return copy(item);
    }

    public synchronized DeleteResult delete(long id) {
        find(id);
        store.remove(id);
        return new DeleteResult(List.of(id), 1);
    }

    public synchronized DeleteResult deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) {
            throw badRequest("삭제할 ids를 1개부터 100개까지 지정하세요.");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw badRequest("ids에는 중복된 ID를 넣을 수 없습니다.");
        }
        if (ids.stream().anyMatch(id -> id == null || id < 1)) {
            throw badRequest("ids에는 1 이상의 ID만 지정하세요.");
        }
        // 없는 ID가 섞여 있을 때 일부 상품만 삭제되는 것을 막는다.
        ids.forEach(this::find);
        ids.forEach(store::remove);
        return new DeleteResult(List.copyOf(ids), ids.size());
    }

    private ItemDto find(long id) {
        if (id < 1) {
            throw badRequest("id는 1 이상이어야 합니다.");
        }
        ItemDto item = store.get(id);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "상품을 찾을 수 없습니다: " + id);
        }
        return item;
    }

    private void validateNameAndPrice(String name, Integer price) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw badRequest("name은 공백이 아닌 1자부터 100자 사이의 문자열이어야 합니다.");
        }
        validatePrice(price);
    }

    private void validatePrice(Integer price) {
        if (price == null || price < 0) {
            throw badRequest("price는 0 이상의 정수여야 합니다.");
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
    }

    private ItemDto copy(ItemDto source) {
        ItemDto item = new ItemDto();
        item.setId(source.getId());
        item.setName(source.getName());
        item.setPrice(source.getPrice());
        return item;
    }
}
