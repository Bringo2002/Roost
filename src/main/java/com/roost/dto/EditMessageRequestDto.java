package com.roost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Typed request body for {@code PUT /api/chat/{messageId}} (edit a
 * message). Both fields are mandatory — an edit without new ciphertext
 * or a new nonce is meaningless in an E2EE conversation.
 */
public class EditMessageRequestDto {

    @NotBlank(message = "content is required")
    @Size(max = 65_536, message = "content exceeds maximum length")
    private String content;

    @NotBlank(message = "nonce is required")
    @Size(max = 64, message = "nonce exceeds maximum length")
    private String nonce;

    public EditMessageRequestDto() {}

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
}
