package kr.ac.jbnu.isy.wsdteaching.api.response;

import kr.ac.jbnu.isy.wsdteaching.api.dto.ItemDto;

public record HeaderItemResult(ItemDto item, String userId, boolean authorizationProvided) {
}
