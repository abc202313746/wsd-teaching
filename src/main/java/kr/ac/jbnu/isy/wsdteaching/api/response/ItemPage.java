package kr.ac.jbnu.isy.wsdteaching.api.response;

import kr.ac.jbnu.isy.wsdteaching.api.dto.ItemDto;
import java.util.List;

public record ItemPage(List<ItemDto> items, long total, int page, int size) {
}
