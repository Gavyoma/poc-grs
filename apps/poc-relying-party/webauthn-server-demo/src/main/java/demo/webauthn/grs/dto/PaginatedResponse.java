package demo.webauthn.grs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;


@Data
@AllArgsConstructor
public class PaginatedResponse {
    List<RevocationWDash> data;
    String nextCursor;
    boolean hasMore;
}
