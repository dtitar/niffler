package guru.qa.niffler.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import guru.qa.niffler.data.projection.SumByCategoryInfo;

import java.util.List;

public record StatisticV2Json(
        @JsonProperty("total")
        Double total,
        @JsonProperty("statByCategories")
        List<SumByCategoryInfo> statByCategories) {

}
