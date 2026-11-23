package com.example.literaturesearch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CaptchaResponse {

    private String captchaId;

    /** data URI（data:image/png;base64,...），可直接作为 <img src> */
    private String image;
}
