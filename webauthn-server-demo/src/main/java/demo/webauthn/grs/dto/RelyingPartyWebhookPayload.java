package demo.webauthn.grs.dto;

import lombok.Data;

import java.util.List;

@Data
public class RelyingPartyWebhookPayload {
    List<RevocationWc> items;
}
