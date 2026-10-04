package kr.ac.jbnu.isy.wsdteaching.api.response;

import java.util.List;

public record DeleteResult(List<Long> deletedIds, int deletedCount) {
}
